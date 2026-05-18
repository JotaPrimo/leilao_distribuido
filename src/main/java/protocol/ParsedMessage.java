package protocol;

import java.util.List;

/**
 * Resultado estruturado do parse de uma mensagem do protocolo.
 *
 * Guarda o tipo do comando e seus argumentos como texto. Conversoes especificas,
 * como double para BID, ficam em metodos auxiliares.
 */
public class ParsedMessage {
    /** Tipo do comando identificado. */
    private final MessageType type;
    /** Argumentos posicionais do comando. */
    private final List<String> args;

    /**
     * Cria uma mensagem parseada.
     *
     * @param type tipo do comando
     * @param args argumentos do comando
     */
    public ParsedMessage(MessageType type, List<String> args) {
        this.type = type;
        this.args = List.copyOf(args);
    }

    /**
     * @return tipo do comando
     */
    public MessageType getType() {
        return type;
    }

    /**
     * Retorna um argumento textual pelo indice.
     *
     * @param index posicao do argumento
     * @return argumento como string
     */
    public String getArg(int index) {
        return args.get(index);
    }

    /**
     * Retorna um argumento convertido para double.
     *
     * @param index posicao do argumento
     * @return argumento convertido para numero
     */
    public double getDoubleArg(int index) {
        return Double.parseDouble(getArg(index));
    }
}
