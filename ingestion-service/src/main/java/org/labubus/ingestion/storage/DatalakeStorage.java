package org.labubus.ingestion.storage;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatalakeStorage {
	private static final Logger logger = LoggerFactory.getLogger(DatalakeStorage.class);

	private final String datalakePath;
	private final int bucketSize;
	private final Path downloadedBooksFile;

	public DatalakeStorage(String datalakePath, int bucketSize) {
		this.datalakePath = datalakePath;
		this.bucketSize = bucketSize;
		this.downloadedBooksFile = Paths.get(datalakePath, "downloaded_books.txt");
		initializeDatalake();
	}

	private void initializeDatalake() {
		try {
			// Create datalake directory if it doesn't exist
			Files.createDirectories(Paths.get(datalakePath));

			// Create downloaded books tracking file if it doesn't exist
			if (!Files.exists(downloadedBooksFile)) {
				Files.createFile(downloadedBooksFile);
				logger.info("Created downloaded_books.txt tracking file");
			}

			logger.info("Datalake initialized at: {}", datalakePath);
		} catch (IOException e) {
			logger.error("Failed to initialize datalake", e);
			throw new RuntimeException("Failed to initialize datalake", e);
		}
	}

	/**
	 * Calculate which bucket a book belongs to
	 */
	private int calculateBucket(int bookId) {
		return bookId / bucketSize;
	}

	/**
	 * Get the bucket directory path for a book
	 */
	private Path getBucketPath(int bookId) {
		int bucket = calculateBucket(bookId);
		return Paths.get(datalakePath, "bucket_" + bucket);
	}

	/**
	 * Save book header and body to datalake
	 */
	public String saveBook(int bookId, String header, String body) throws IOException {
		Path bucketPath = getBucketPath(bookId);

		Files.createDirectories(bucketPath);

		Path headerPath = bucketPath.resolve(bookId + "_header.txt");
		Files.writeString(headerPath, header);

		Path bodyPath = bucketPath.resolve(bookId + "_body.txt");
		Files.writeString(bodyPath, body);

		trackDownloadedBook(bookId);

		logger.info("Saved book {} to bucket {}", bookId, bucketPath);
		return bucketPath.toString();
	}

	/**
	 * Check if a book has been downloaded
	 */
	public boolean isBookDownloaded(int bookId) {
		try {
			Set<Integer> downloadedBooks = getDownloadedBooks();
			return downloadedBooks.contains(bookId);
		} catch (IOException e) {
			logger.error("Error checking if book is downloaded", e);
			return false;
		}
	}

	/**
	 * Get the path where a book is stored (if it exists)
	 */
	public String getBookPath(int bookId) {
		if (isBookDownloaded(bookId)) {
			return getBucketPath(bookId).toString();
		}
		return null;
	}

	/**
	 * Track a downloaded book
	 */
	private void trackDownloadedBook(int bookId) throws IOException {
		Set<Integer> downloadedBooks = getDownloadedBooks();
		downloadedBooks.add(bookId);

		List<Integer> sortedBooks = new ArrayList<>(downloadedBooks);
		Collections.sort(sortedBooks);

		StringBuilder content = new StringBuilder();
		for (int id : sortedBooks) {
			content.append(id).append("\n");
		}

		Files.writeString(downloadedBooksFile, content.toString());
	}

	/**
	 * Get all downloaded book IDs
	 */
	public Set<Integer> getDownloadedBooks() throws IOException {
		Set<Integer> books = new HashSet<>();

		if (!Files.exists(downloadedBooksFile)) {
			return books;
		}

		List<String> lines = Files.readAllLines(downloadedBooksFile);
		for (String line : lines) {
			line = line.trim();
			if (!line.isEmpty()) {
				try {
					books.add(Integer.parseInt(line));
				} catch (NumberFormatException e) {
					logger.warn("Invalid book ID in tracking file: {}", line);
				}
			}
		}

		return books;
	}

	/**
	 * Get list of all downloaded book IDs as a sorted list
	 */
	public List<Integer> getDownloadedBooksList() throws IOException {
		List<Integer> books = new ArrayList<>(getDownloadedBooks());
		Collections.sort(books);
		return books;
	}

	/**
	 * Get count of downloaded books
	 */
	public int getDownloadedBooksCount() throws IOException {
		return getDownloadedBooks().size();
	}
}