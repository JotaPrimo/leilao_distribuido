package server;

/**
 * Representa o item que esta sendo leiloado.
 *
 * O item define o nome exibido aos clientes, o lance minimo e a duracao total
 * do leilao em segundos.
 */
public class AuctionItem {
    /** Nome do produto enviado na mensagem START. */
    private final String name;
    /** Menor valor aceito como base inicial do leilao. */
    private final double minimumBid;
    /** Tempo total do leilao em segundos. */
    private final int durationSeconds;

    /**
     * Cria um item de leilao.
     *
     * @param name nome do item
     * @param minimumBid lance minimo inicial
     * @param durationSeconds duracao em segundos
     */
    public AuctionItem(String name, double minimumBid, int durationSeconds) {
        this.name = name;
        this.minimumBid = minimumBid;
        this.durationSeconds = durationSeconds;
    }

    /**
     * @return nome do item
     */
    public String getName() {
        return name;
    }

    /**
     * @return lance minimo do item
     */
    public double getMinimumBid() {
        return minimumBid;
    }

    /**
     * @return duracao do leilao em segundos
     */
    public int getDurationSeconds() {
        return durationSeconds;
    }
}
