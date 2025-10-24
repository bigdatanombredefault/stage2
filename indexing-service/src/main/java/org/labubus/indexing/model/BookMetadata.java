package org.labubus.indexing.model;

public record BookMetadata(
		int bookId,
		String title,
		String author,
		String language,
		Integer year,
		String path
) {
	@Override
	public String toString() {
		return String.format("BookMetadata{id=%d, title='%s', author='%s', lang='%s', year=%d}",
				bookId, title, author, language, year);
	}
}