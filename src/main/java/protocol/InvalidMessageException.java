package protocol;

/**
 * Excecao usada quando uma linha recebida nao segue o protocolo esperado.
 *
 * A mensagem da excecao e um codigo curto, como nome_invalido ou valor_invalido,
 * que pode ser reutilizado na resposta enviada ao cliente.
 */
public class InvalidMessageException extends RuntimeException {
    /**
     * Cria a excecao com o codigo do erro de protocolo.
     *
     * @param message codigo do erro
     */
    public InvalidMessageException(String message) {
        super(message);
    }
}
