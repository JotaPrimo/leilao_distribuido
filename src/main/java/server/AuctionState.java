package server;

/**
 * Retrato persistivel do estado do leilao.
 *
 * Esta classe e imutavel: depois de criada, apenas expõe getters. Ela e usada
 * para salvar/restaurar o maior lance, vencedor, tempo restante e atividade.
 */
public class AuctionState {
    /** Nome do item salvo. */
    private final String itemName;
    /** Maior lance salvo. */
    private final double currentBid;
    /** Usuario vencedor salvo, ou string vazia se nao houver. */
    private final String currentWinner;
    /** Segundos restantes no momento em que o estado foi salvo. */
    private final int remainingSeconds;
    /** Indica se o leilao estava ativo quando foi salvo. */
    private final boolean active;

    /**
     * Cria um retrato do estado do leilao.
     */
    public AuctionState(String itemName, double currentBid, String currentWinner, int remainingSeconds, boolean active) {
        this.itemName = itemName;
        this.currentBid = currentBid;
        this.currentWinner = currentWinner;
        this.remainingSeconds = remainingSeconds;
        this.active = active;
    }

    /**
     * @return nome do item salvo
     */
    public String getItemName() {
        return itemName;
    }

    /**
     * @return maior lance salvo
     */
    public double getCurrentBid() {
        return currentBid;
    }

    /**
     * @return vencedor salvo, ou string vazia se nao houver
     */
    public String getCurrentWinner() {
        return currentWinner;
    }

    /**
     * @return segundos restantes salvos
     */
    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    /**
     * @return true se o leilao estava ativo
     */
    public boolean isActive() {
        return active;
    }
}
