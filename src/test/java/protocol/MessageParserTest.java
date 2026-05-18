package protocol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("MessageParser - Protocolo de Mensagens")
class MessageParserTest {
    @Test
    void shouldParseValidLogin() {
        ParsedMessage msg = MessageParser.parse("LOGIN JoaoSilva");

        assertEquals(MessageType.LOGIN, msg.getType());
        assertEquals("JoaoSilva", msg.getArg(0));
    }

    @Test
    void shouldRejectLoginWithoutUsername() {
        assertThrows(InvalidMessageException.class, () -> MessageParser.parse("LOGIN"));
    }

    @Test
    void shouldRejectLoginWithSpaceInUsername() {
        assertThrows(InvalidMessageException.class, () -> MessageParser.parse("LOGIN Joao Silva"));
    }

    @Test
    void shouldRejectLoginWithEmptyUsername() {
        assertThrows(InvalidMessageException.class, () -> MessageParser.parse("LOGIN "));
    }

    @Test
    void shouldRejectLoginWithUsernameTooLong() {
        assertThrows(InvalidMessageException.class, () -> MessageParser.parse("LOGIN " + "A".repeat(21)));
    }

    @Test
    void shouldParseValidBid() {
        ParsedMessage msg = MessageParser.parse("BID 1600.50");

        assertEquals(MessageType.BID, msg.getType());
        assertEquals(1600.50, msg.getDoubleArg(0), 0.001);
    }

    @Test
    void shouldRejectBidWithoutValue() {
        assertThrows(InvalidMessageException.class, () -> MessageParser.parse("BID"));
    }

    @Test
    void shouldRejectBidWithNonNumericValue() {
        assertThrows(InvalidMessageException.class, () -> MessageParser.parse("BID abc"));
    }

    @Test
    void shouldAllowParsingNegativeBidSoManagerCanRejectIt() {
        ParsedMessage msg = MessageParser.parse("BID -100.00");
        assertEquals(-100.00, msg.getDoubleArg(0), 0.001);
    }

    @Test
    void shouldRejectUnknownCommand() {
        assertThrows(InvalidMessageException.class, () -> MessageParser.parse("HACK 1234"));
    }

    @Test
    void shouldRejectEmptyMessage() {
        assertThrows(InvalidMessageException.class, () -> MessageParser.parse(""));
    }

    @Test
    void shouldRejectNullMessage() {
        assertThrows(InvalidMessageException.class, () -> MessageParser.parse(null));
    }
}
