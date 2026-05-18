package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import protocol.InvalidMessageException;
import protocol.MessageParser;
import protocol.MessageType;
import protocol.ParsedMessage;

/**
 * Trata a conexao de um unico cliente TCP.
 *
 * Cada instancia roda em uma thread do servidor. Ela le as linhas enviadas pelo
 * cliente, valida o protocolo com MessageParser e delega login/lance para o
 * AuctionManager.
 */
public class ClientHandler implements Runnable {
    /** Socket do cliente atendido por esta thread. */
    private final Socket socket;
    /** Gerenciador compartilhado entre todos os clientes. */
    private final AuctionManager auctionManager;
    /** Entrada de texto do cliente. Pode ser injetada em testes. */
    private BufferedReader reader;
    /** Saida de texto para o cliente. Pode ser injetada em testes. */
    private PrintWriter writer;
    /** Usuario autenticado nesta conexao. Null enquanto nao houver LOGIN valido. */
    private String username;

    /**
     * Cria o handler usado pelo servidor real.
     *
     * @param socket conexao TCP do cliente
     * @param auctionManager gerenciador do leilao compartilhado
     */
    public ClientHandler(Socket socket, AuctionManager auctionManager) {
        this.socket = socket;
        this.auctionManager = auctionManager;
    }

    /**
     * Cria um handler com reader/writer ja prontos, facilitando testes unitarios.
     */
    ClientHandler(Socket socket, BufferedReader reader, PrintWriter writer, AuctionManager auctionManager) {
        this.socket = socket;
        this.reader = reader;
        this.writer = writer;
        this.auctionManager = auctionManager;
    }

    /**
     * Abre os streams do socket, processa mensagens ate a desconexao e remove o
     * cliente da lista de broadcast ao final.
     */
    @Override
    public void run() {
        try {
            if (reader == null || writer == null) {
                try (Socket clientSocket = socket;
                     BufferedReader socketReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8))) {
                    reader = socketReader;
                    writer = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8), true);
                    runMessageLoop();
                }
            } else {
                runMessageLoop();
            }
        } catch (IOException e) {
            auctionManager.log("CLIENT " + (username == null ? "UNKNOWN" : username), "IO_ERROR " + e.getMessage());
        } finally {
            auctionManager.removeClient(username, writer);
        }
    }

    /**
     * Processa mensagens continuamente ate o cliente fechar a conexao.
     *
     * @throws IOException se a leitura do cliente falhar
     */
    private void runMessageLoop() throws IOException {
        while (processNextMessage()) {
            // processNextMessage controls protocol state and returns false on EOF.
        }
    }

    /**
     * Le e processa uma unica linha do cliente.
     *
     * O metodo retorna false quando a conexao terminou. Ele e package-private
     * para permitir testes sem precisar abrir socket real.
     *
     * @return true se uma mensagem foi processada; false se chegou EOF
     * @throws IOException se a leitura falhar
     */
    boolean processNextMessage() throws IOException {
        String line = reader.readLine();
        if (line == null) {
            return false;
        }

        try {
            ParsedMessage message = MessageParser.parse(line);
            if (message.getType() == MessageType.LOGIN) {
                handleLogin(message.getArg(0));
            } else {
                handleBid(message.getDoubleArg(0));
            }
        } catch (InvalidMessageException e) {
            writer.println(username == null ? "ERR_LOGIN " + e.getMessage() : "ERR_BID " + e.getMessage());
        }
        return true;
    }

    /**
     * Tenta autenticar o cliente com o nome recebido no comando LOGIN.
     *
     * @param requestedUsername nome de usuario ja validado pelo parser
     */
    private void handleLogin(String requestedUsername) {
        if (username != null) {
            writer.println("ERR_LOGIN ja_autenticado");
            return;
        }

        AuctionManager.LoginResult loginResult = auctionManager.registerClient(requestedUsername, writer);
        if (!loginResult.isSuccess()) {
            writer.println("ERR_LOGIN " + loginResult.getError());
            return;
        }

        username = requestedUsername;
        writer.println("OK_LOGIN");
        auctionManager.sendCurrentState(writer);
    }

    /**
     * Tenta registrar um lance enviado pelo cliente autenticado.
     *
     * @param value valor numerico ja validado pelo parser
     */
    private void handleBid(double value) {
        if (username == null) {
            writer.println("ERR_BID login_obrigatorio");
            return;
        }

        AuctionManager.BidResult result = auctionManager.placeBid(username, value);
        if (result.isSuccess()) {
            writer.println("OK_BID");
        } else {
            writer.println("ERR_BID " + result.getError());
        }
    }
}
