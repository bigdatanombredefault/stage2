package org.labubus.ingestion.model;

public class IngestionStatusResponse {
	private int bookId;
	private String status;
	private String path;

	private IngestionStatusResponse(int bookId, String status, String path) {
		this.bookId = bookId;
		this.status = status;
		this.path = path;
	}

	public static IngestionStatusResponse available(int bookId, String path) {
		return new IngestionStatusResponse(bookId, "available", path);
	}

	public static IngestionStatusResponse notFound(int bookId) {
		return new IngestionStatusResponse(bookId, "not_found", null);
	}

	public int getBookId() { return bookId; }
	public String getStatus() {return status; }
	public String getPath() { return path; }

	public void setBookId(int bookId) { this.bookId = bookId; }
	public void setStatus(String status) { this.status = status; }
	public void setPath(String path) { this.path = path; }
}