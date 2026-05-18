package server;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Servidor TCP do sistema de leilao.
 *
 * A classe abre uma porta, aceita conexoes de clientes e entrega cada cliente a
 * um ClientHandler. O limite de clientes simultaneos e controlado pelo pool de
 * threads.
 */
public class AuctionServer {
    /** Porta usada quando nenhuma porta e passada por argumento. */
    public static final int DEFAULT_PORT = 12345;
    /** Quantidade maxima de clientes simultaneos. */
    public static final int MAX_CLIENTS = 5;

    /** Porta TCP onde o servidor escuta conexoes. */
    private final int port;
    /** Gerenciador que guarda o estado e regras do leilao. */
    private final AuctionManager auctionManager;
    /** Pool fixo que executa um ClientHandler por cliente conectado. */
    private ThreadPoolExecutor executor;
    /** ServerSocket aberto, guardado para permitir stop() em testes. */
    private ServerSocket serverSocket;
    /** Flag usada para encerrar o loop de accept. */
    private volatile boolean running;

    /**
     * Cria um servidor com item customizado.
     *
     * @param port porta TCP
     * @param item item que sera leiloado
     */
    public AuctionServer(int port, AuctionItem item) {
        this(port, new AuctionManager(item));
    }

    /**
     * Cria um servidor com um gerenciador ja configurado.
     *
     * @param port porta TCP
     * @param auctionManager gerenciador compartilhado pelo servidor
     */
    public AuctionServer(int port, AuctionManager auctionManager) {
        this.port = port;
        this.auctionManager = auctionManager;
    }

    /**
     * Ponto de entrada da aplicacao servidor.
     *
     * Le a porta opcional, pergunta se deve restaurar estado salvo e entao
     * inicia o servidor bloqueando a thread principal.
     */
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        AuctionManager auctionManager = AuctionManager.getInstance();
        PersistenceManager persistenceManager = new PersistenceManager(PersistenceManager.DEFAULT_FILE);

        AuctionState restoredState = null;
        if (persistenceManager.exists()) {
            System.out.print("Leilao anterior encontrado. Continuar? (s/n): ");
            Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
            String answer = scanner.nextLine().trim();
            if (answer.equalsIgnoreCase("s")) {
                restoredState = persistenceManager.load();
            }
        }

        auctionManager.startAuction(restoredState);
        new AuctionServer(port, auctionManager).start(false);
    }

    /**
     * Inicia um leilao novo e passa a aceitar conexoes.
     *
     * Este metodo bloqueia enquanto o servidor estiver rodando.
     */
    public void start() {
        auctionManager.startAuction();
        start(false);
    }

    /**
     * Inicia o loop TCP do servidor.
     *
     * @param startAuction true para iniciar leilao dentro deste metodo; false
     * quando o chamador ja iniciou/restaurou o leilao antes
     */
    private void start(boolean startAuction) {
        if (startAuction) {
            auctionManager.startAuction();
        }
        executor = new ThreadPoolExecutor(
            MAX_CLIENTS,
            MAX_CLIENTS,
            0L,
            TimeUnit.MILLISECONDS,
            new SynchronousQueue<>(),
            new ThreadPoolExecutor.AbortPolicy()
        );

        running = true;
        auctionManager.log("SERVER", "Escutando na porta " + port);

        try (ServerSocket openedServerSocket = new ServerSocket(port)) {
            serverSocket = openedServerSocket;
            while (running) {
                Socket socket = serverSocket.accept();
                try {
                    // Cada cliente fica em sua propria thread do pool.
                    executor.execute(new ClientHandler(socket, auctionManager));
                } catch (RuntimeException rejected) {
                    try (Socket rejectedSocket = socket;
                         PrintWriter writer = new PrintWriter(new OutputStreamWriter(rejectedSocket.getOutputStream(), StandardCharsets.UTF_8), true)) {
                        writer.println("ERR_LOGIN servidor_cheio");
                    }
                    auctionManager.log("SERVER", "Conexao recusada: limite de clientes atingido");
                }
            }
        } catch (IOException e) {
            if (running) {
                auctionManager.log("SERVER", "IO_ERROR " + e.getMessage());
            }
        } finally {
            running = false;
            if (executor != null) {
                executor.shutdownNow();
            }
        }
    }

    /**
     * Para o servidor, fecha a porta TCP e encerra o leilao.
     *
     * Usado principalmente pelos testes de integracao para liberar a porta.
     */
    public void stop() {
        running = false;
        auctionManager.forceEnd();
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            auctionManager.log("SERVER", "Erro ao fechar servidor: " + e.getMessage());
        }
    }
}
