package org.labubus.ingestion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.labubus.ingestion.service.BookIngestionService;
import org.labubus.ingestion.storage.DatalakeStorage;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class IngestionServiceTest {

	@Test
	public void testDatalakeStorage(@TempDir Path tempDir) throws Exception {
		// Create storage with temp directory
		DatalakeStorage storage = new DatalakeStorage(tempDir.toString(), 10);

		// Save a book
		String path = storage.saveBook(5, "Header content", "Body content");

		// Verify it was saved
		assertTrue(storage.isBookDownloaded(5));
		assertNotNull(storage.getBookPath(5));
		assertEquals(1, storage.getDownloadedBooksCount());

		System.out.println("DatalakeStorage test passed!");
	}

	@Test
	public void testBookDownload(@TempDir Path tempDir) throws Exception {
		// Create storage
		DatalakeStorage storage = new DatalakeStorage(tempDir.toString(), 10);

		// Create service
		BookIngestionService service = new BookIngestionService(
				storage,
				"https://www.gutenberg.org/cache/epub",
				30000
		);

		// Download a small book (Alice in Wonderland = 11)
		System.out.println("Downloading book 11 (Alice in Wonderland)...");
		String path = service.downloadAndSave(11);

		// Verify
		assertNotNull(path);
		assertTrue(service.isBookDownloaded(11));

		System.out.println("Book download test passed!");
		System.out.println("Saved to: " + path);
	}

	@Test
	public void testBookNotFound(@TempDir Path tempDir) {
		DatalakeStorage storage = new DatalakeStorage(tempDir.toString(), 10);
		BookIngestionService service = new BookIngestionService(
				storage,
				"https://www.gutenberg.org/cache/epub",
				30000
		);

		// Try to download a non-existent book
		IOException exception = assertThrows(IOException.class, () -> {
			service.downloadAndSave(999999);
		});

		System.out.println("\nTest: Book Not Found");
		System.out.println("Error message: " + exception.getMessage());

		// Should mention attempted URLs
		assertTrue(exception.getMessage().contains("Failed to download book 999999"));
		assertTrue(exception.getMessage().contains("Attempted URLs"));

		// Verify nothing was saved
		assertFalse(service.isBookDownloaded(999999));

		System.out.println("Book not found test passed!\n");
	}

	@Test
	public void testInvalidBookFormat(@TempDir Path tempDir) throws Exception {
		DatalakeStorage storage = new DatalakeStorage(tempDir.toString(), 10);

		// Create a mock book with invalid format (no markers)
		String invalidContent = "This is a book\nwithout proper markers\nJust plain text";

		// We need to test the splitHeaderBody method indirectly
		// by creating a temporary file and trying to process it

		// For now, just verify the logic - we'll test this when we have the full controller
		System.out.println("Invalid format test structure ready (will be tested via API)\n");
	}
}