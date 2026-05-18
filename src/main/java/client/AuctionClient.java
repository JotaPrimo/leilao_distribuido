package client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * Cliente de console do sistema de leilao.
 *
 * Abre uma conexao TCP com o servidor, envia LOGIN, cria uma thread para escutar
 * mensagens do servidor e usa ConsoleUI para ler lances digitados pelo usuario.
 */
public class AuctionClient {
    /**
     * Ponto de entrada do cliente.
     *
     * @param args args[0] pode conter host e args[1] pode conter porta
     */
    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 12345;

        try (Socket socket = new Socket(host, port);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
             Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {

            // Primeiro passo do protocolo: identificar o usuario.
            System.out.print("Usuario: ");
            String username = scanner.nextLine().trim();
            writer.println("LOGIN " + username);

            // Se o login falhar, o cliente encerra sem permitir lances.
            String loginResponse = reader.readLine();
            if (loginResponse == null) {
                System.out.println("Servidor encerrou a conexao.");
                return;
            }
            System.out.println(loginResponse);
            if (!loginResponse.equals("OK_LOGIN")) {
                return;
            }

            // Mensagens START, UPDATE e END chegam de forma assincrona.
            Thread listener = new Thread(new ServerListener(reader), "server-listener");
            listener.setDaemon(true);
            listener.start();

            new ConsoleUI(writer, scanner).run();
        }
    }
}
