package server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AuctionManager - Logica e Concorrencia")
class AuctionManagerTest {
    private Path stateFile;
    private AuctionManager manager;

    @BeforeEach
    void setUp() throws IOException {
        stateFile = Files.createTempFile("auction-manager-test", ".json");
        manager = new AuctionManager(
            new AuctionItem("Notebook_Dell", 1500.00, 60),
            new PersistenceManager(stateFile.toString())
        );
        manager.startAuction();
    }

    @AfterEach
    void tearDown() throws IOException {
        manager.forceEnd();
        Files.deleteIfExists(stateFile);
    }

    @Test
    void shouldAcceptBidAboveMinimum() {
        AuctionManager.BidResult result = manager.placeBid("Alice", 1600.00);

        assertTrue(result.isSuccess());
        assertEquals(1600.00, manager.getCurrentBid(), 0.001);
        assertEquals("Alice", manager.getCurrentWinner());
    }

    @Test
    void shouldAcceptBidAboveCurrentBid() {
        manager.placeBid("Alice", 1600.00);
        AuctionManager.BidResult result = manager.placeBid("Bob", 1700.00);

        assertTrue(result.isSuccess());
        assertEquals(1700.00, manager.getCurrentBid(), 0.001);
        assertEquals("Bob", manager.getCurrentWinner());
    }

    @Test
    void shouldAcceptBidJustAboveMinimum() {
        assertTrue(manager.placeBid("Alice", 1500.01).isSuccess());
    }

    @Test
    void shouldRejectBidEqualToMinimum() {
        AuctionManager.BidResult result = manager.placeBid("Alice", 1500.00);

        assertFalse(result.isSuccess());
        assertEquals("lance_baixo", result.getError());
        assertEquals(0.0, manager.getCurrentBid(), 0.001);
    }

    @Test
    void shouldRejectBidBelowMinimum() {
        assertFalse(manager.placeBid("Alice", 999.00).isSuccess());
        assertNull(manager.getCurrentWinner());
    }

    @Test
    void shouldRejectBidEqualToCurrentBid() {
        manager.placeBid("Alice", 1600.00);
        AuctionManager.BidResult result = manager.placeBid("Bob", 1600.00);

        assertFalse(result.isSuccess());
        assertEquals("Alice", manager.getCurrentWinner());
    }

    @Test
    void shouldRejectBidLowerThanCurrentBid() {
        manager.placeBid("Alice", 2000.00);
        AuctionManager.BidResult result = manager.placeBid("Bob", 1800.00);

        assertFalse(result.isSuccess());
        assertEquals("Alice", manager.getCurrentWinner());
        assertEquals(2000.00, manager.getCurrentBid(), 0.001);
    }

    @Test
    void shouldRejectZeroAndNegativeBids() {
        assertFalse(manager.placeBid("Alice", 0.0).isSuccess());
        assertFalse(manager.placeBid("Alice", -100.0).isSuccess());
    }

    @Test
    void shouldRejectBidAfterAuctionEnds() {
        manager.placeBid("Alice", 1600.00);
        manager.forceEnd();

        AuctionManager.BidResult result = manager.placeBid("Bob", 2000.00);

        assertFalse(result.isSuccess());
        assertEquals("tempo_esgotado", result.getError());
        assertEquals("Alice", manager.getCurrentWinner());
    }

    @Test
    void shouldBeInactiveAfterEnd() {
        assertTrue(manager.isActive());
        manager.forceEnd();
        assertFalse(manager.isActive());
    }

    @Test
    void shouldAcceptOnlyOneConcurrentBidAtSameValue() throws InterruptedException {
        int threadCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger acceptedCount = new AtomicInteger();
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            String user = "User" + i;
            threads[i] = new Thread(() -> {
                try {
                    startLatch.await();
                    if (manager.placeBid(user, 1600.00).isSuccess()) {
                        acceptedCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            threads[i].start();
        }

        startLatch.countDown();
        for (Thread thread : threads) {
            thread.join(2000);
        }

        assertEquals(1, acceptedCount.get());
    }

    @Test
    void shouldMaintainConsistentStateUnderConcurrentBids() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch done = new CountDownLatch(10);

        for (int t = 0; t < 10; t++) {
            int threadId = t;
            pool.submit(() -> {
                try {
                    for (int b = 0; b < 10; b++) {
                        manager.placeBid("User" + threadId, 1501.0 + (threadId * 100.0) + b);
                    }
                } finally {
                    done.countDown();
                }
            });
        }

        assertTrue(done.await(5, TimeUnit.SECONDS));
        pool.shutdownNow();
        assertTrue(manager.getCurrentBid() >= 1501.0);
        assertNotNull(manager.getCurrentWinner());
    }

    @Test
    void shouldBroadcastOncePerAcceptedBidAndEndOnClose() {
        List<String> broadcasts = new CopyOnWriteArrayList<>();
        manager.setBroadcastListener(broadcasts::add);

        manager.placeBid("Alice", 1600.00);
        manager.placeBid("Bob", 500.00);
        manager.placeBid("Carol", 1700.00);
        manager.forceEnd();

        assertEquals(3, broadcasts.size());
        assertEquals("UPDATE Alice 1600.00", broadcasts.get(0));
        assertEquals("UPDATE Carol 1700.00", broadcasts.get(1));
        assertEquals("END Carol 1700.00", broadcasts.get(2));
    }

    @Test
    void shouldBroadcastEndNinguemWhenNoBids() {
        List<String> broadcasts = new CopyOnWriteArrayList<>();
        manager.setBroadcastListener(broadcasts::add);

        manager.forceEnd();

        assertTrue(broadcasts.contains("END NINGUEM 0.00"));
    }
}
