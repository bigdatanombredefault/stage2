package org.labubus.indexing.service;

import org.labubus.indexing.model.BookMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MetadataExtractor {
	private static final Logger logger = LoggerFactory.getLogger(MetadataExtractor.class);

	// Regex patterns for extracting metadata
	private static final Pattern TITLE_PATTERN = Pattern.compile("Title:\\s*(.+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern AUTHOR_PATTERN = Pattern.compile("Author:\\s*(.+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern LANGUAGE_PATTERN = Pattern.compile("Language:\\s*(.+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern RELEASE_DATE_PATTERN = Pattern.compile("Release Date:\\s*.*?(\\d{4})", Pattern.CASE_INSENSITIVE);

	/**
	 * Extract metadata from book header
	 */
	public BookMetadata extractMetadata(int bookId, String header, String path) {
		String title = extractTitle(header);
		String author = extractAuthor(header);
		String language = extractLanguage(header);
		Integer year = extractYear(header);

		// Clean up extracted values
		title = cleanString(title);
		author = cleanString(author);
		language = cleanString(language);

		// Use defaults if extraction failed
		if (title == null || title.isEmpty()) {
			title = "Unknown Title (Book " + bookId + ")";
		}
		if (author == null || author.isEmpty()) {
			author = "Unknown Author";
		}
		if (language == null || language.isEmpty()) {
			language = "en"; // Default to English
		}

		BookMetadata metadata = new BookMetadata(bookId, title, author, language, year, path);
		logger.debug("Extracted metadata: {}", metadata);

		return metadata;
	}

	private String extractTitle(String header) {
		Matcher matcher = TITLE_PATTERN.matcher(header);
		if (matcher.find()) {
			return matcher.group(1).trim();
		}
		return null;
	}

	private String extractAuthor(String header) {
		Matcher matcher = AUTHOR_PATTERN.matcher(header);
		if (matcher.find()) {
			return matcher.group(1).trim();
		}
		return null;
	}

	private String extractLanguage(String header) {
		Matcher matcher = LANGUAGE_PATTERN.matcher(header);
		if (matcher.find()) {
			return matcher.group(1).trim().toLowerCase();
		}
		return null;
	}

	private Integer extractYear(String header) {
		Matcher matcher = RELEASE_DATE_PATTERN.matcher(header);
		if (matcher.find()) {
			try {
				return Integer.parseInt(matcher.group(1));
			} catch (NumberFormatException e) {
				logger.warn("Failed to parse year: {}", matcher.group(1));
			}
		}
		return null;
	}

	/**
	 * Clean and normalize extracted string
	 */
	private String cleanString(String str) {
		if (str == null) {
			return null;
		}

		// Remove extra whitespace
		str = str.replaceAll("\\s+", " ").trim();

		// Truncate if too long
		if (str.length() > 300) {
			str = str.substring(0, 297) + "...";
		}

		return str;
	}
}