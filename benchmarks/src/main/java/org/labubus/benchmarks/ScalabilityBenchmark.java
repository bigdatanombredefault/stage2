package org.labubus.benchmarks;

import com.google.gson.Gson;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class ScalabilityBenchmark {

    private Connection sqliteConn;
    private Gson gson;
    private Map<String, Set<Integer>> invertedIndex;

    @Param({"10", "100", "1000"})
    private int existingBooks;

    @Setup(Level.Trial)
    public void setup() throws SQLException, IOException {
        gson = new Gson();

        // Create database with existing books
        String dbPath = "scalability_test_" + existingBooks + ".db";
        sqliteConn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        createSchema();
        populateBooks(existingBooks);
        createInvertedIndex(existingBooks);

        System.out.println("Setup complete: " + existingBooks + " books in database");
    }

    @TearDown(Level.Trial)
    public void tearDown() throws SQLException {
        if (sqliteConn != null) sqliteConn.close();
    }

    /**
     * Benchmark: Insert new book into database
     */
    @Benchmark
    public void insertNewBook(Blackhole blackhole) throws SQLException {
        int newBookId = existingBooks + 10000; // Unique ID

        String sql = "INSERT INTO books (book_id, title, author, language, year, path) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = sqliteConn.prepareStatement(sql)) {
            stmt.setInt(1, newBookId);
            stmt.setString(2, "New Book");
            stmt.setString(3, "New Author");
            stmt.setString(4, "english");
            stmt.setInt(5, 2024);
            stmt.setString(6, "bucket/" + newBookId + ".txt");

            int result = stmt.executeUpdate();
            blackhole.consume(result);

            // Cleanup
            try (Statement cleanup = sqliteConn.createStatement()) {
                cleanup.execute("DELETE FROM books WHERE book_id = " + newBookId);
            }
        }
    }

    /**
     * Benchmark: Search for book by ID
     */
    @Benchmark
    public void searchBookById(Blackhole blackhole) throws SQLException {
        int searchId = existingBooks / 2;

        String sql = "SELECT * FROM books WHERE book_id = ?";
        try (PreparedStatement stmt = sqliteConn.prepareStatement(sql)) {
            stmt.setInt(1, searchId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    blackhole.consume(rs.getString("title"));
                }
            }
        }
    }

    /**
     * Benchmark: Update inverted index with new words
     */
    @Benchmark
    public void updateIndex(Blackhole blackhole) {
        int newBookId = existingBooks + 20000;

        // Add 100 new words to index
        for (int i = 0; i < 100; i++) {
            String word = "word" + i;
            invertedIndex.computeIfAbsent(word, k -> new HashSet<>()).add(newBookId);
        }

        blackhole.consume(invertedIndex.size());

        // Cleanup
        for (int i = 0; i < 100; i++) {
            String word = "word" + i;
            Set<Integer> books = invertedIndex.get(word);
            if (books != null) {
                books.remove(newBookId);
            }
        }
    }

    /**
     * Benchmark: Search inverted index
     */
    @Benchmark
    public void searchIndex(Blackhole blackhole) {
        Set<Integer> results = invertedIndex.getOrDefault("the", Collections.emptySet());
        blackhole.consume(results);
    }

    /**
     * Benchmark: Serialize index to JSON
     */
    @Benchmark
    public void saveIndexToJson(Blackhole blackhole) {
        String json = gson.toJson(invertedIndex);
        blackhole.consume(json.length());
    }

    /**
     * Benchmark: Full-text search by author
     */
    @Benchmark
    public void searchByAuthor(Blackhole blackhole) throws SQLException {
        String sql = "SELECT * FROM books WHERE author LIKE ? LIMIT 10";

        try (PreparedStatement stmt = sqliteConn.prepareStatement(sql)) {
            stmt.setString(1, "%Author%");

            List<String> titles = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    titles.add(rs.getString("title"));
                }
            }
            blackhole.consume(titles);
        }
    }

    // Helper methods
    private void createSchema() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS books (
                book_id INTEGER PRIMARY KEY,
                title TEXT NOT NULL,
                author TEXT,
                language TEXT,
                year INTEGER,
                path TEXT
            )
            """;

        try (Statement stmt = sqliteConn.createStatement()) {
            stmt.execute(sql);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_author ON books(author)");
        }
    }

    private void populateBooks(int count) throws SQLException {
        String sql = "INSERT INTO books VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = sqliteConn.prepareStatement(sql)) {
            for (int i = 1; i <= count; i++) {
                stmt.setInt(1, i);
                stmt.setString(2, "Book " + i);
                stmt.setString(3, "Author " + (i % 50));
                stmt.setString(4, "english");
                stmt.setInt(5, 2000 + (i % 25));
                stmt.setString(6, "bucket/" + i + ".txt");
                stmt.executeUpdate();
            }
        }
    }

    private void createInvertedIndex(int bookCount) {
        invertedIndex = new HashMap<>();

        // Common words (appear in 50% of books)
        String[] common = {"the", "a", "and", "or", "but"};
        for (String word : common) {
            Set<Integer> books = new HashSet<>();
            for (int i = 1; i <= bookCount; i++) {
                if (i % 2 == 0) books.add(i);
            }
            invertedIndex.put(word, books);
        }

        // Rare words (each book has unique words)
        for (int i = 1; i <= bookCount; i++) {
            for (int j = 0; j < 5; j++) {
                String word = "book" + i + "_word" + j;
                invertedIndex.put(word, new HashSet<>(Set.of(i)));
            }
        }
    }
}