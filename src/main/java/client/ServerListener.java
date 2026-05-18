package client;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * Escuta continuamente as mensagens enviadas pelo servidor.
 *
 * Roda em uma thread separada para que o usuario possa digitar lances enquanto
 * mensagens START, UPDATE, ERR_BID e END aparecem no terminal.
 */
public class ServerListener implements Runnable {
    /** Entrada conectada ao socket do servidor. */
    private final BufferedReader reader;

    /**
     * Cria o listener.
     *
     * @param reader entrada do servidor
     */
    public ServerListener(BufferedReader reader) {
        this.reader = reader;
    }

    /**
     * Imprime cada linha recebida do servidor ate a conexao fechar.
     */
    @Override
    public void run() {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
            System.out.println("Conexao com o servidor encerrada.");
        } catch (IOException e) {
            System.out.println("Conexao perdida: " + e.getMessage());
        }
    }
}
