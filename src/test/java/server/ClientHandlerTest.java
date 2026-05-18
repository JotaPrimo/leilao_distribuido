package server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ClientHandler - Fluxo de Mensagens por Cliente")
class ClientHandlerTest {
    private Path stateFile;
    private AuctionManager manager;
    private StringWriter output;

    @BeforeEach
    void setUp() throws IOException {
        stateFile = Files.createTempFile("client-handler-test", ".json");
        manager = new AuctionManager(
            new AuctionItem("Notebook_Dell", 1500.00, 60),
            new PersistenceManager(stateFile.toString())
        );
        manager.startAuction();
        output = new StringWriter();
    }

    @AfterEach
    void tearDown() throws IOException {
        manager.forceEnd();
        Files.deleteIfExists(stateFile);
    }

    @Test
    void shouldRegisterUserAndSendOkLogin() throws Exception {
        ClientHandler handler = handlerFor("LOGIN Alice\n");

        handler.processNextMessage();

        assertTrue(output.toString().contains("OK_LOGIN"));
        assertTrue(output.toString().contains("START Notebook_Dell 1500.00"));
    }

    @Test
    void shouldRejectBidBeforeLogin() throws Exception {
        ClientHandler handler = handlerFor("BID 1500.00\n");

        handler.processNextMessage();

        assertTrue(output.toString().contains("ERR_BID login_obrigatorio"));
    }

    @Test
    void shouldRespondOkBidWhenManagerAccepts() throws Exception {
        ClientHandler handler = handlerFor("LOGIN Alice\nBID 1600.00\n");

        handler.processNextMessage();
        handler.processNextMessage();

        assertTrue(output.toString().contains("OK_BID"));
        assertTrue(output.toString().contains("UPDATE Alice 1600.00"));
    }

    @Test
    void shouldRespondErrBidWhenManagerRejects() throws Exception {
        ClientHandler handler = handlerFor("LOGIN Alice\nBID 500.00\n");

        handler.processNextMessage();
        handler.processNextMessage();

        assertTrue(output.toString().contains("ERR_BID lance_baixo"));
    }

    @Test
    void shouldRespondErrBidWithTimeExpiredReasonAfterEnd() throws Exception {
        ClientHandler handler = handlerFor("LOGIN Alice\nBID 1600.00\n");
        manager.forceEnd();

        handler.processNextMessage();
        handler.processNextMessage();

        assertTrue(output.toString().contains("ERR_BID tempo_esgotado"));
    }

    @Test
    void shouldReturnErrForUnknownCommand() throws Exception {
        ClientHandler handler = handlerFor("LOGIN Alice\nUNKNOWN_CMD\n");

        handler.processNextMessage();
        assertDoesNotThrow(() -> handler.processNextMessage());

        assertTrue(output.toString().contains("ERR_BID comando_invalido"));
    }

    @Test
    void shouldReturnErrBidForNonNumericValue() throws Exception {
        ClientHandler handler = handlerFor("LOGIN Alice\nBID abc\n");

        handler.processNextMessage();
        handler.processNextMessage();

        assertTrue(output.toString().contains("ERR_BID valor_invalido"));
    }

    @Test
    void shouldRejectDuplicateActiveUsername() throws Exception {
        ClientHandler first = handlerFor("LOGIN Alice\n");
        first.processNextMessage();
        output = new StringWriter();
        ClientHandler second = handlerFor("LOGIN Alice\n");

        second.processNextMessage();

        assertTrue(output.toString().contains("ERR_LOGIN nome_duplicado"));
    }

    private ClientHandler handlerFor(String input) {
        BufferedReader reader = new BufferedReader(new StringReader(input));
        PrintWriter writer = new PrintWriter(output, true);
        return new ClientHandler(null, reader, writer, manager);
    }
}
