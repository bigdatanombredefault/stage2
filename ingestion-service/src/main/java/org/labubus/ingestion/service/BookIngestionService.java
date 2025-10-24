package org.labubus.ingestion.service;

import org.labubus.ingestion.storage.DatalakeStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class BookIngestionService {
	private static final Logger logger = LoggerFactory.getLogger(BookIngestionService.class);

	private final DatalakeStorage storage;
	private final String gutenbergBaseUrl;
	private final int downloadTimeout;

	public BookIngestionService(DatalakeStorage storage, String gutenbergBaseUrl, int downloadTimeout) {
		this.storage = storage;
		this.gutenbergBaseUrl = gutenbergBaseUrl;
		this.downloadTimeout = downloadTimeout;
	}

	/**
	 * Download a book from Project Gutenberg and save it to datalake
	 * @param bookId The Project Gutenberg book ID
	 * @return The path where the book was saved
	 * @throws IOException if download or save fails
	 */
	public String downloadAndSave(int bookId) throws IOException {
		logger.info("Starting download for book {}", bookId);

		// Download the book content
		String bookContent = downloadBook(bookId);

		// Split into header and body (throws exception if invalid format)
		String[] parts = splitHeaderBody(bookContent);
		String header = parts[0];
		String body = parts[1];

		// Save to datalake
		String path = storage.saveBook(bookId, header, body);

		logger.info("Successfully downloaded and saved book {} to {}", bookId, path);
		return path;
	}

	/**
	 * Download book content from Project Gutenberg
	 */
	private String downloadBook(int bookId) throws IOException {
		// Try different URL formats - Project Gutenberg has multiple formats
		String[] urlFormats = {
				String.format("%s/%d/pg%d.txt", gutenbergBaseUrl, bookId, bookId),
				String.format("%s/%d/%d.txt", gutenbergBaseUrl, bookId, bookId),
				String.format("%s/%d/%d-0.txt", gutenbergBaseUrl, bookId, bookId)
		};

		IOException lastException = null;
		StringBuilder attemptedUrls = new StringBuilder();

		for (String urlString : urlFormats) {
			try {
				logger.debug("Trying URL: {}", urlString);
				String content = downloadFromUrl(urlString);
				logger.info("Successfully downloaded book {} from {}", bookId, urlString);
				return content;
			} catch (IOException e) {
				logger.debug("Failed to download from {}: {}", urlString, e.getMessage());
				attemptedUrls.append("\n  - ").append(urlString);
				lastException = e;
			}
		}

		throw new IOException(
				String.format("Failed to download book %d. Attempted URLs:%s", bookId, attemptedUrls),
				lastException
		);
	}

	/**
	 * Download content from a specific URL
	 */
	private String downloadFromUrl(String urlString) throws IOException {
		URL url = new URL(urlString);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("GET");
		connection.setConnectTimeout(downloadTimeout);
		connection.setReadTimeout(downloadTimeout);
		connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Stage2 Book Ingestion Service)");

		int responseCode = connection.getResponseCode();
		if (responseCode != 200) {
			throw new IOException("HTTP " + responseCode + " for URL: " + urlString);
		}

		StringBuilder content = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				content.append(line).append("\n");
			}
		}

		return content.toString();
	}

	/**
	 * Split book content into header and body
	 * Header = everything before "*** START OF"
	 * Body = everything after "*** START OF" and before "*** END OF"
	 *
	 * @throws IOException if markers are not found (invalid book format)
	 */
	private String[] splitHeaderBody(String content) throws IOException {
		// Find the start marker
		String startMarker = "*** START OF";
		int startIndex = content.indexOf(startMarker);

		if (startIndex == -1) {
			throw new IOException("Invalid book format: START marker not found. " +
					"This book may not be in plain text format or may be corrupted.");
		}

		// Find end marker
		String endMarker = "*** END OF";
		int endIndex = content.indexOf(endMarker, startIndex);

		if (endIndex == -1) {
			throw new IOException("Invalid book format: END marker not found. " +
					"This book may be incomplete or corrupted.");
		}

		// Extract header (everything before START marker)
		String header = content.substring(0, startIndex).trim();

		// Extract body (between START and END markers)
		String body = content.substring(startIndex, endIndex).trim();

		// Remove the START marker line itself from body
		body = body.replaceFirst("\\*\\*\\* START OF[^\\n]*\\n", "").trim();

		// Validate that we actually have content
		if (header.isEmpty()) {
			throw new IOException("Invalid book format: Header is empty");
		}

		if (body.isEmpty()) {
			throw new IOException("Invalid book format: Body is empty");
		}

		logger.debug("Successfully split book - Header: {} chars, Body: {} chars",
				header.length(), body.length());

		return new String[]{header, body};
	}

	/**
	 * Check if a book is already downloaded
	 */
	public boolean isBookDownloaded(int bookId) {
		return storage.isBookDownloaded(bookId);
	}

	/**
	 * Get the path of a downloaded book
	 */
	public String getBookPath(int bookId) {
		return storage.getBookPath(bookId);
	}
}