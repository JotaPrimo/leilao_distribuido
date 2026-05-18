package server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PersistenceManager - Salvar e Restaurar Estado")
class PersistenceManagerTest {
    private Path stateFile;
    private PersistenceManager persistence;

    @BeforeEach
    void setUp() throws IOException {
        stateFile = Files.createTempFile("auction-state-test", ".json");
        Files.deleteIfExists(stateFile);
        persistence = new PersistenceManager(stateFile.toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(stateFile);
    }

    @Test
    void shouldReportMissingFile() {
        assertFalse(persistence.exists());
    }

    @Test
    void shouldSaveStateFile() throws IOException {
        persistence.save(new AuctionState("Notebook", 1600.00, "Alice", 30, true));

        assertTrue(persistence.exists());
        assertTrue(Files.readString(stateFile).contains("\"currentWinner\": \"Alice\""));
    }

    @Test
    void shouldLoadSavedState() throws IOException {
        persistence.save(new AuctionState("Notebook", 1600.00, "Alice", 30, true));

        AuctionState loaded = persistence.load();

        assertEquals("Notebook", loaded.getItemName());
        assertEquals(1600.00, loaded.getCurrentBid(), 0.001);
        assertEquals("Alice", loaded.getCurrentWinner());
        assertEquals(30, loaded.getRemainingSeconds());
        assertTrue(loaded.isActive());
    }

    @Test
    void shouldOverwritePreviousState() throws IOException {
        persistence.save(new AuctionState("Notebook", 1600.00, "Alice", 30, true));
        persistence.save(new AuctionState("Notebook", 2000.00, "Bob", 10, true));

        AuctionState loaded = persistence.load();

        assertEquals(2000.00, loaded.getCurrentBid(), 0.001);
        assertEquals("Bob", loaded.getCurrentWinner());
        assertEquals(10, loaded.getRemainingSeconds());
    }

    @Test
    void shouldEscapeAndRestoreQuotesAndBackslashes() throws IOException {
        persistence.save(new AuctionState("Note\\book", 1600.00, "Ali\"ce", 30, true));

        AuctionState loaded = persistence.load();

        assertEquals("Note\\book", loaded.getItemName());
        assertEquals("Ali\"ce", loaded.getCurrentWinner());
    }

    @Test
    void shouldUseDefaultsForMissingFields() throws IOException {
        Files.writeString(stateFile, "{\n  \"currentBid\": 1500.00\n}\n");

        AuctionState loaded = persistence.load();

        assertEquals(AuctionManager.DEFAULT_ITEM_NAME, loaded.getItemName());
        assertEquals(1500.00, loaded.getCurrentBid(), 0.001);
        assertEquals("", loaded.getCurrentWinner());
        assertEquals(0, loaded.getRemainingSeconds());
        assertFalse(loaded.isActive());
    }

    @Test
    void shouldThrowWhenNumericFieldsAreCorrupted() throws IOException {
        Files.writeString(stateFile, "{\n  \"currentBid\": \"abc\"\n}\n");

        assertThrows(NumberFormatException.class, () -> persistence.load());
    }
}
