package client;

import java.io.PrintWriter;
import java.util.Locale;
import java.util.Scanner;

/**
 * Interface simples de terminal para digitar lances.
 *
 * A classe le valores digitados pelo usuario e envia comandos BID ao servidor.
 */
public class ConsoleUI {
    /** Saida conectada ao socket do servidor. */
    private final PrintWriter writer;
    /** Entrada do terminal. */
    private final Scanner scanner;

    /**
     * Cria a interface de console.
     *
     * @param writer saida para o servidor
     * @param scanner entrada do usuario
     */
    public ConsoleUI(PrintWriter writer, Scanner scanner) {
        this.writer = writer;
        this.scanner = scanner;
    }

    /**
     * Inicia o loop de leitura do terminal.
     *
     * Valores numericos sao enviados como BID. A palavra "sair" encerra o
     * cliente localmente.
     */
    public void run() {
        System.out.println("Digite o valor do lance ou 'sair' para encerrar.");
        while (scanner.hasNextLine()) {
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("sair")) {
                break;
            }
            if (input.isEmpty()) {
                continue;
            }
            try {
                double value = Double.parseDouble(input.replace(',', '.'));
                // O protocolo sempre usa ponto decimal e Locale.US.
                writer.println(String.format(Locale.US, "BID %.2f", value));
            } catch (NumberFormatException e) {
                System.out.println("Valor invalido.");
            }
        }
    }
}
