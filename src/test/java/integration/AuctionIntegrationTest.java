package integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import server.AuctionItem;
import server.AuctionManager;
import server.AuctionServer;
import server.PersistenceManager;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AuctionIntegration - Servidor e Cliente TCP")
class AuctionIntegrationTest {
    private Path stateFile;
    private int port;
    private AuctionServer server;
    private Thread serverThread;

    @BeforeEach
    void setUp() throws Exception {
        stateFile = Files.createTempFile("auction-integration-test", ".json");
        port = findFreePort();
        AuctionManager manager = new AuctionManager(
            new AuctionItem("Notebook_Dell", 1500.00, 30),
            new PersistenceManager(stateFile.toString())
        );
        server = new AuctionServer(port, manager);
        serverThread = new Thread(server::start, "auction-server-test");
        serverThread.setDaemon(true);
        serverThread.start();
        waitForServer();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.stop();
        serverThread.join(1000);
        Files.deleteIfExists(stateFile);
    }

    @Test
    void shouldLoginAndReceiveStart() throws Exception {
        try (TestClient alice = connect()) {
            alice.send("LOGIN Alice");

            assertEquals("OK_LOGIN", alice.readLine());
            assertTrue(alice.readLine().startsWith("START Notebook_Dell 1500.00"));
        }
    }

    @Test
    void shouldRejectInvalidLogin() throws Exception {
        try (TestClient client = connect()) {
            client.send("LOGIN Nome Com Espaco");

            assertEquals("ERR_LOGIN nome_invalido", client.readLine());
        }
    }

    @Test
    void shouldAcceptValidBidAndBroadcastUpdate() throws Exception {
        try (TestClient alice = loggedClient("Alice")) {
            alice.send("BID 1600.00");

            List<String> lines = alice.readUntilBidResult();
            assertTrue(lines.contains("OK_BID"));
            assertTrue(lines.contains("UPDATE Alice 1600.00"));
        }
    }

    @Test
    void shouldRejectBidBelowCurrentValue() throws Exception {
        try (TestClient alice = loggedClient("Alice");
             TestClient bob = loggedClient("Bob")) {
            alice.send("BID 1700.00");
            alice.readUntilBidResult();

            bob.send("BID 1600.00");

            assertEquals("ERR_BID lance_baixo", bob.readUntilBidResult().getLast());
        }
    }

    @Test
    void shouldContinueAuctionAfterClientDisconnects() throws Exception {
        TestClient alice = loggedClient("Alice");
        TestClient bob = loggedClient("Bob");

        alice.close();
        bob.send("BID 1600.00");

        assertTrue(bob.readUntilBidResult().contains("OK_BID"));
        bob.close();
    }

    @Test
    void shouldRejectBidBeforeLogin() throws Exception {
        try (TestClient client = connect()) {
            client.send("BID 1600.00");

            assertEquals("ERR_BID login_obrigatorio", client.readLine());
        }
    }

    @Test
    void shouldNotCrashServerOnMalformedMessage() throws Exception {
        try (TestClient alice = loggedClient("Alice")) {
            alice.send("GARBAGE_COMMAND !!!");
            assertEquals("ERR_BID comando_invalido", alice.readLine());
        }

        try (TestClient bob = loggedClient("Bob")) {
            assertNotNull(bob);
        }
    }

    @Test
    void shouldAcceptOnlyOneBidFromConcurrentTcpClients() throws Exception {
        int clientCount = 5;
        CountDownLatch ready = new CountDownLatch(clientCount);
        CountDownLatch go = new CountDownLatch(1);
        CopyOnWriteArrayList<String> responses = new CopyOnWriteArrayList<>();
        List<Thread> threads = new ArrayList<>();
        List<TestClient> clients = new ArrayList<>();

        for (int i = 0; i < clientCount; i++) {
            clients.add(loggedClient("User" + i));
        }

        for (TestClient client : clients) {
            Thread thread = new Thread(() -> {
                try {
                    ready.countDown();
                    go.await();
                    client.send("BID 1600.00");
                    responses.add(client.readUntilBidResult().getLast());
                } catch (Exception e) {
                    responses.add("ERROR");
                }
            });
            threads.add(thread);
            thread.start();
        }

        ready.await();
        go.countDown();
        for (Thread thread : threads) {
            thread.join(3000);
        }
        for (TestClient client : clients) {
            client.close();
        }

        assertEquals(1, responses.stream().filter("OK_BID"::equals).count());
        assertEquals(4, responses.stream().filter("ERR_BID lance_baixo"::equals).count());
    }

    private TestClient loggedClient(String username) throws Exception {
        TestClient client = connect();
        client.send("LOGIN " + username);
        assertEquals("OK_LOGIN", client.readLine());
        assertTrue(client.readLine().startsWith("START Notebook_Dell 1500.00"));
        return client;
    }

    private TestClient connect() throws IOException {
        return new TestClient(new Socket("localhost", port));
    }

    private void waitForServer() throws Exception {
        IOException last = null;
        for (int i = 0; i < 50; i++) {
            try (Socket ignored = new Socket("localhost", port)) {
                return;
            } catch (IOException e) {
                last = e;
                Thread.sleep(50);
            }
        }
        throw last;
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    static class TestClient implements Closeable {
        private final Socket socket;
        private final BufferedReader reader;
        private final PrintWriter writer;

        TestClient(Socket socket) throws IOException {
            this.socket = socket;
            this.socket.setSoTimeout(3000);
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        }

        void send(String msg) {
            writer.println(msg);
        }

        String readLine() throws IOException {
            return reader.readLine();
        }

        List<String> readUntilBidResult() throws IOException {
            List<String> lines = new ArrayList<>();
            while (true) {
                String line = reader.readLine();
                lines.add(line);
                if (line == null || line.equals("OK_BID") || line.startsWith("ERR_BID")) {
                    return lines;
                }
            }
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
