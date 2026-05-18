package server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Salva e carrega o estado do leilao em um arquivo JSON simples.
 *
 * A implementacao usa apenas bibliotecas padrao do Java. Como o formato tem
 * poucos campos conhecidos, o load faz uma leitura simples por linhas.
 */
public class PersistenceManager {
    /** Arquivo usado pela aplicacao principal para guardar o estado. */
    public static final String DEFAULT_FILE = "auction_state.json";

    /** Caminho completo do arquivo usado por esta instancia. */
    private final Path filePath;

    /**
     * Cria o gerenciador apontando para um arquivo especifico.
     *
     * @param fileName caminho do arquivo de estado
     */
    public PersistenceManager(String fileName) {
        this.filePath = Path.of(fileName);
    }

    /**
     * Verifica se ja existe estado salvo.
     *
     * @return true quando o arquivo existe
     */
    public boolean exists() {
        return Files.exists(filePath);
    }

    /**
     * Grava o estado atual do leilao em disco.
     *
     * O metodo e synchronized para evitar escritas simultaneas no mesmo arquivo.
     *
     * @param state estado a salvar
     * @throws IOException se nao for possivel escrever o arquivo
     */
    public synchronized void save(AuctionState state) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
            writer.write("{\n");
            writer.write("  \"itemName\": \"" + escape(state.getItemName()) + "\",\n");
            writer.write("  \"currentBid\": " + String.format(java.util.Locale.US, "%.2f", state.getCurrentBid()) + ",\n");
            writer.write("  \"currentWinner\": \"" + escape(state.getCurrentWinner()) + "\",\n");
            writer.write("  \"remainingSeconds\": " + state.getRemainingSeconds() + ",\n");
            writer.write("  \"active\": " + state.isActive() + "\n");
            writer.write("}\n");
        }
    }

    /**
     * Carrega o estado salvo em disco.
     *
     * Campos ausentes recebem valores padrao. Campos numericos invalidos geram
     * NumberFormatException, indicando arquivo corrompido.
     *
     * @return estado lido do arquivo
     * @throws IOException se nao for possivel ler o arquivo
     */
    public synchronized AuctionState load() throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.contains(":")) {
                    continue;
                }
                String[] parts = trimmed.split(":", 2);
                String key = clean(parts[0]);
                String value = clean(parts[1]);
                values.put(key, value);
            }
        }

        String itemName = values.getOrDefault("itemName", AuctionManager.DEFAULT_ITEM_NAME);
        double currentBid = Double.parseDouble(values.getOrDefault("currentBid", "0"));
        String currentWinner = values.getOrDefault("currentWinner", "");
        int remainingSeconds = Integer.parseInt(values.getOrDefault("remainingSeconds", "0"));
        boolean active = Boolean.parseBoolean(values.getOrDefault("active", "false"));
        return new AuctionState(itemName, currentBid, currentWinner, remainingSeconds, active);
    }

    /**
     * Limpa uma chave ou valor lido do JSON simples.
     *
     * @param value texto bruto da linha
     * @return texto sem aspas, virgula final e escapes basicos
     */
    private static String clean(String value) {
        String cleaned = value.trim();
        if (cleaned.endsWith(",")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    /**
     * Escapa caracteres especiais antes de escrever no JSON.
     *
     * @param value texto original
     * @return texto seguro para ser escrito entre aspas
     */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
