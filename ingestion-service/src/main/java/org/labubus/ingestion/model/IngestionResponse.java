package org.labubus.ingestion.model;

public class IngestionResponse {
	private int bookId;
	private String status;
	private String path;
	private String message;

	private IngestionResponse(int bookId, String status, String path, String message) {
		this.bookId = bookId;
		this.status = status;
		this.path = path;
		this.message = message;
	}

	public static IngestionResponse success(int bookId, String path) {
		return new IngestionResponse(bookId, "downloaded", path, null);
	}

	public static IngestionResponse alreadyExists(int bookId, String path) {
		return new IngestionResponse(bookId, "already_exists", path, null);
	}

	public static IngestionResponse failure(int bookId, String message) {
		return new IngestionResponse(bookId, "failed", null, message);
	}

	public int getBookId() { return bookId; }
	public String getStatus() { return status; }
	public String getPath() { return path; }
	public String getMessage() { return message; }

	public void setBookId(int bookId) { this.bookId = bookId; }
	public void setStatus(String status) { this.status = status; }
	public void setPath(String path) { this.path = path; }
	public void setMessage(String message) { this.message = message; }
}