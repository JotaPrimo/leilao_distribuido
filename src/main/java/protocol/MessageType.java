package protocol;

/**
 * Tipos de comandos aceitos no protocolo cliente-servidor.
 */
public enum MessageType {
    /** Identificacao inicial do cliente: LOGIN <usuario>. */
    LOGIN,
    /** Envio de lance: BID <valor>. */
    BID
}
