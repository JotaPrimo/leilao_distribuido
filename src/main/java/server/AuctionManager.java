package server;

import java.io.PrintWriter;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Centraliza a regra de negocio do leilao.
 *
 * Esta classe guarda o maior lance, o vencedor atual, os clientes conectados,
 * controla o tempo do leilao e envia mensagens de broadcast para todos os
 * clientes. Os metodos que alteram o lance usam lock para evitar race condition.
 */
public class AuctionManager {
    /** Nome do item usado quando o sistema roda com a configuracao padrao. */
    public static final String DEFAULT_ITEM_NAME = "Notebook_Dell";
    /** Valor minimo inicial do leilao padrao. */
    public static final double DEFAULT_MINIMUM_BID = 1500.00;
    /** Duracao padrao do leilao em segundos. */
    public static final int DEFAULT_DURATION_SECONDS = 300;

    /** Instancia unica usada pela aplicacao principal em linha de comando. */
    private static final AuctionManager INSTANCE = new AuctionManager();
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** Protege a validacao e atualizacao do maior lance. */
    private final ReentrantLock bidLock = new ReentrantLock();
    /** Protege a lista de clientes ativos durante login, logout e broadcast. */
    private final Object clientsLock = new Object();
    /** Mantem usuarios que ja conectaram alguma vez, permitindo identificar reconexao. */
    private final Set<String> knownUsers = new HashSet<>();
    /** Mapeia nome de usuario ativo para o writer usado no broadcast. */
    private final Map<String, PrintWriter> activeClients = new HashMap<>();
    /** Responsavel por salvar e carregar o estado do leilao em disco. */
    private final PersistenceManager persistenceManager;

    /** Item leiloado nesta instancia. */
    private AuctionItem item;
    /** Maior valor ja aceito. Antes do primeiro lance, fica igual ao minimo. */
    private volatile double currentBid = DEFAULT_MINIMUM_BID;
    /** Usuario vencedor atual. String vazia indica que ainda nao houve lance. */
    private volatile String currentWinner = "";
    /** Momento de encerramento em milissegundos desde epoch. */
    private volatile long endTimeMillis;
    /** Indica se o leilao ainda aceita lances. */
    private volatile boolean auctionActive;
    /** Timer que chama o encerramento automatico quando o prazo acaba. */
    private Timer timer;
    /** Hook usado nos testes para observar mensagens de broadcast sem abrir socket. */
    private Consumer<String> broadcastListener;

    /**
     * Construtor privado usado apenas pela instancia singleton da aplicacao.
     */
    private AuctionManager() {
        this(new AuctionItem(DEFAULT_ITEM_NAME, DEFAULT_MINIMUM_BID, DEFAULT_DURATION_SECONDS),
                new PersistenceManager(PersistenceManager.DEFAULT_FILE));
    }

    /**
     * Cria um gerenciador com item customizado e arquivo de persistencia padrao.
     *
     * @param item item que sera leiloado
     */
    public AuctionManager(AuctionItem item) {
        this(item, new PersistenceManager(PersistenceManager.DEFAULT_FILE));
    }

    /**
     * Cria um gerenciador totalmente configuravel, util para testes.
     *
     * @param item item que sera leiloado
     * @param persistenceManager persistencia usada para salvar o estado
     */
    public AuctionManager(AuctionItem item, PersistenceManager persistenceManager) {
        this.item = item;
        this.persistenceManager = persistenceManager;
    }

    /**
     * Retorna a instancia unica usada pelo servidor iniciado via main.
     *
     * @return gerenciador singleton da aplicacao
     */
    public static AuctionManager getInstance() {
        return INSTANCE;
    }

    /**
     * Inicia um leilao novo ou restaura um leilao salvo.
     *
     * Se o estado restaurado estiver ativo e ainda tiver tempo restante, o maior
     * lance e vencedor sao recuperados. Caso contrario, o leilao comeca do zero.
     *
     * @param restoredState estado carregado do disco, ou null para iniciar novo
     */
    public void startAuction(AuctionState restoredState) {
        bidLock.lock();
        try {
            int duration = item.getDurationSeconds();
            if (restoredState != null && restoredState.isActive() && restoredState.getRemainingSeconds() > 0) {
                currentBid = Math.max(restoredState.getCurrentBid(), item.getMinimumBid());
                currentWinner = restoredState.getCurrentWinner();
                duration = restoredState.getRemainingSeconds();
                log("SERVER", "Leilao restaurado com " + duration + "s restantes");
            } else {
                currentBid = item.getMinimumBid();
                currentWinner = "";
                log("SERVER", "Novo leilao iniciado");
            }

            auctionActive = true;
            endTimeMillis = System.currentTimeMillis() + duration * 1000L;
            scheduleEnd(duration);
            broadcast(String.format(Locale.US, "START %s %.2f %d", item.getName(), item.getMinimumBid(), duration));
            persistState();
        } finally {
            bidLock.unlock();
        }
    }

    /**
     * Inicia um leilao novo, sem restaurar estado anterior.
     */
    public void startAuction() {
        startAuction(null);
    }

    /**
     * Registra um cliente conectado para receber broadcasts do leilao.
     *
     * @param username nome solicitado pelo cliente
     * @param writer saida TCP do cliente
     * @return resultado do login, com sucesso ou motivo do erro
     */
    public LoginResult registerClient(String username, PrintWriter writer) {
        if (username == null || !username.matches("[A-Za-z0-9]{1,20}")) {
            return LoginResult.error("nome_invalido");
        }

        synchronized (clientsLock) {
            if (activeClients.containsKey(username)) {
                return LoginResult.error("nome_duplicado");
            }
            boolean reconnecting = knownUsers.contains(username);
            knownUsers.add(username);
            activeClients.put(username, writer);
            log("CLIENT " + username, reconnecting ? "RECONNECT" : "LOGIN");
            return LoginResult.ok(reconnecting);
        }
    }

    /**
     * Remove um cliente da lista de broadcasts quando ele desconecta.
     *
     * A remocao confere o mesmo writer para nao remover uma reconexao nova do
     * mesmo usuario por engano.
     *
     * @param username usuario a remover
     * @param writer writer associado a conexao que terminou
     */
    public void removeClient(String username, PrintWriter writer) {
        if (username == null) {
            return;
        }
        synchronized (clientsLock) {
            PrintWriter current = activeClients.get(username);
            if (current == writer) {
                activeClients.remove(username);
                log("CLIENT " + username, "DISCONNECT");
            }
        }
    }

    /**
     * Envia ao cliente recem-conectado o estado atual do leilao.
     *
     * Sempre envia START. Se ja houver lance, tambem envia UPDATE. Se o leilao
     * ja terminou, envia END para que o cliente fique sincronizado.
     *
     * @param writer saida do cliente
     */
    public void sendCurrentState(PrintWriter writer) {
        int remaining = getRemainingSeconds();
        writer.println(String.format(Locale.US, "START %s %.2f %d", item.getName(), item.getMinimumBid(), remaining));
        if (!currentWinner.isEmpty()) {
            writer.println(String.format(Locale.US, "UPDATE %s %.2f", currentWinner, currentBid));
        }
        if (!auctionActive) {
            writer.println(buildEndMessage());
        }
    }

    /**
     * Tenta registrar um lance.
     *
     * O metodo e protegido por lock porque varios clientes podem dar lance ao
     * mesmo tempo. Apenas um thread por vez valida se o valor e maior que o
     * lance atual, atualiza o vencedor, envia UPDATE e persiste o estado.
     *
     * @param username usuario que enviou o lance
     * @param value valor do lance
     * @return sucesso ou motivo da rejeicao
     */
    public BidResult placeBid(String username, double value) {
        bidLock.lock();
        try {
            if (!auctionActive) {
                return BidResult.error("tempo_esgotado");
            }
            if (value <= currentBid) {
                return BidResult.error("lance_baixo");
            }
            currentBid = value;
            currentWinner = username;
            log("CLIENT " + username, String.format(Locale.US, "BID %.2f", value));
            broadcast(String.format(Locale.US, "UPDATE %s %.2f", username, value));
            persistState();
            return BidResult.ok();
        } finally {
            bidLock.unlock();
        }
    }

    /**
     * Informa se o leilao ainda aceita lances.
     *
     * @return true enquanto o tempo nao acabou e o leilao nao foi encerrado
     */
    public boolean isActive() {
        return auctionActive;
    }

    /**
     * Retorna o maior lance real feito por usuarios.
     *
     * @return 0.0 quando ainda nao ha lances; caso contrario, o maior lance
     */
    public double getCurrentBid() {
        return currentWinner == null || currentWinner.isEmpty() ? 0.0 : currentBid;
    }

    /**
     * Retorna o usuario vencedor atual.
     *
     * @return nome do vencedor, ou null se ainda nao ha lances
     */
    public String getCurrentWinner() {
        return currentWinner == null || currentWinner.isEmpty() ? null : currentWinner;
    }

    /**
     * Registra um observador de broadcast usado principalmente pelos testes.
     *
     * @param broadcastListener funcao chamada para cada mensagem enviada
     */
    public void setBroadcastListener(Consumer<String> broadcastListener) {
        this.broadcastListener = broadcastListener;
    }

    /**
     * Encerra o leilao imediatamente.
     *
     * E usado em testes e tambem pelo servidor ao parar, garantindo que o timer
     * nao continue vivo depois do encerramento manual.
     */
    public void forceEnd() {
        endAuction();
        if (timer != null) {
            timer.cancel();
        }
    }

    /**
     * Agenda o encerramento automatico do leilao apos a duracao configurada.
     *
     * @param durationSeconds segundos ate o fim do leilao
     */
    private void scheduleEnd(int durationSeconds) {
        if (timer != null) {
            timer.cancel();
        }
        timer = new Timer("auction-timer", true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                endAuction();
            }
        }, Math.max(0, durationSeconds) * 1000L);
    }

    /**
     * Encerra o leilao, envia END para todos e persiste o estado final.
     */
    private void endAuction() {
        bidLock.lock();
        try {
            if (!auctionActive) {
                return;
            }
            auctionActive = false;
            log("SERVER", "Leilao encerrado");
            broadcast(buildEndMessage());
            persistState();
        } finally {
            bidLock.unlock();
        }
    }

    /**
     * Monta a mensagem END no formato do protocolo TCP.
     *
     * @return END com vencedor e valor, ou END NINGUEM 0.00 quando sem lances
     */
    private String buildEndMessage() {
        if (currentWinner == null || currentWinner.isEmpty()) {
            return "END NINGUEM 0.00";
        }
        return String.format(Locale.US, "END %s %.2f", currentWinner, currentBid);
    }

    /**
     * Envia uma mensagem para todos os clientes ativos.
     *
     * Clientes cujo writer acusa erro sao removidos da lista para nao receberem
     * novas mensagens.
     *
     * @param message mensagem ja formatada no protocolo do servidor
     */
    private void broadcast(String message) {
        Consumer<String> listener = broadcastListener;
        if (listener != null) {
            listener.accept(message);
        }
        synchronized (clientsLock) {
            activeClients.entrySet().removeIf(entry -> {
                PrintWriter writer = entry.getValue();
                writer.println(message);
                if (writer.checkError()) {
                    log("CLIENT " + entry.getKey(), "BROADCAST_FAILED");
                    return true;
                }
                return false;
            });
        }
    }

    /**
     * Salva em disco um retrato do estado atual do leilao.
     *
     * Falhas de persistencia sao logadas, mas nao derrubam o servidor.
     */
    private void persistState() {
        try {
            persistenceManager.save(new AuctionState(
                    item.getName(),
                    currentBid,
                    currentWinner,
                    getRemainingSeconds(),
                    auctionActive
            ));
        } catch (Exception e) {
            log("SERVER", "Erro ao persistir estado: " + e.getMessage());
        }
    }

    /**
     * Calcula quantos segundos faltam ate o fim do leilao.
     *
     * @return 0 se o leilao acabou; caso contrario, segundos arredondados para cima
     */
    private int getRemainingSeconds() {
        if (!auctionActive) {
            return 0;
        }
        long millis = endTimeMillis - System.currentTimeMillis();
        return (int) Math.max(0, Math.ceil(millis / 1000.0));
    }

    /**
     * Escreve uma linha de log simples no console.
     *
     * @param source origem do evento, como SERVER ou CLIENT Alice
     * @param message descricao do evento
     */
    public void log(String source, String message) {
        System.out.println("[" + LocalTime.now().format(LOG_TIME) + "] " + source + ": " + message);
    }

    /**
     * Representa o resultado da tentativa de login de um cliente.
     */
    public static class LoginResult {
        private final boolean success;
        private final boolean reconnecting;
        private final String error;

        /**
         * Cria um resultado de login.
         */
        private LoginResult(boolean success, boolean reconnecting, String error) {
            this.success = success;
            this.reconnecting = reconnecting;
            this.error = error;
        }

        /**
         * Cria resultado de login bem-sucedido.
         *
         * @param reconnecting true se o usuario ja havia conectado antes
         * @return resultado de sucesso
         */
        public static LoginResult ok(boolean reconnecting) {
            return new LoginResult(true, reconnecting, null);
        }

        /**
         * Cria resultado de erro no login.
         *
         * @param error codigo de erro enviado ao cliente
         * @return resultado de erro
         */
        public static LoginResult error(String error) {
            return new LoginResult(false, false, error);
        }

        /**
         * @return true quando o login foi aceito
         */
        public boolean isSuccess() {
            return success;
        }

        /**
         * @return true quando o usuario esta reconectando
         */
        public boolean isReconnecting() {
            return reconnecting;
        }

        /**
         * @return codigo de erro, ou null em caso de sucesso
         */
        public String getError() {
            return error;
        }
    }

    /**
     * Representa o resultado da tentativa de registrar um lance.
     */
    public static class BidResult {
        private final boolean success;
        private final String error;

        /**
         * Cria um resultado de lance.
         */
        private BidResult(boolean success, String error) {
            this.success = success;
            this.error = error;
        }

        /**
         * @return resultado de lance aceito
         */
        public static BidResult ok() {
            return new BidResult(true, null);
        }

        /**
         * Cria resultado de lance rejeitado.
         *
         * @param error codigo de erro enviado ao cliente
         * @return resultado de erro
         */
        public static BidResult error(String error) {
            return new BidResult(false, error);
        }

        /**
         * @return true quando o lance foi aceito
         */
        public boolean isSuccess() {
            return success;
        }

        /**
         * @return codigo de erro, ou null em caso de sucesso
         */
        public String getError() {
            return error;
        }
    }
}
