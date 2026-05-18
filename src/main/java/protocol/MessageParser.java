package protocol;

import java.util.List;

/**
 * Converte linhas recebidas pelo TCP em mensagens estruturadas do protocolo.
 *
 * O protocolo aceito pelo servidor e composto por comandos simples:
 * LOGIN <usuario> e BID <valor>. Quando a linha nao segue o formato esperado,
 * o parser lanca InvalidMessageException com um codigo de erro.
 */
public final class MessageParser {
    /**
     * Impede instanciacao, pois a classe possui apenas metodos estaticos.
     */
    private MessageParser() {
    }

    /**
     * Analisa uma linha enviada pelo cliente.
     *
     * @param line linha bruta recebida do socket
     * @return mensagem estruturada com tipo e argumentos
     * @throws InvalidMessageException quando o comando e invalido ou malformado
     */
    public static ParsedMessage parse(String line) {
        if (line == null || line.isEmpty()) {
            throw new InvalidMessageException("mensagem_vazia");
        }

        String[] parts = line.split(" ", -1);
        return switch (parts[0]) {
            case "LOGIN" -> parseLogin(parts);
            case "BID" -> parseBid(parts);
            default -> throw new InvalidMessageException("comando_invalido");
        };
    }

    /**
     * Valida o comando LOGIN.
     *
     * O usuario deve ser alfanumerico e ter de 1 a 20 caracteres.
     */
    private static ParsedMessage parseLogin(String[] parts) {
        if (parts.length != 2 || parts[1].isEmpty() || !parts[1].matches("[A-Za-z0-9]{1,20}")) {
            throw new InvalidMessageException("nome_invalido");
        }
        return new ParsedMessage(MessageType.LOGIN, List.of(parts[1]));
    }

    /**
     * Valida o comando BID.
     *
     * O parser apenas garante que o valor e numerico. A regra de ser maior que
     * o lance atual fica no AuctionManager.
     */
    private static ParsedMessage parseBid(String[] parts) {
        if (parts.length != 2 || parts[1].isEmpty()) {
            throw new InvalidMessageException("valor_invalido");
        }
        try {
            Double.parseDouble(parts[1]);
        } catch (NumberFormatException e) {
            throw new InvalidMessageException("valor_invalido");
        }
        return new ParsedMessage(MessageType.BID, List.of(parts[1]));
    }
}
