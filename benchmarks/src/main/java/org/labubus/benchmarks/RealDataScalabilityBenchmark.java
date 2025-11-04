package org.labubus.benchmarks;

import com.google.gson.Gson;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Scalability benchmarks using REAL books from datalake
 * Tests: Does indexing slow down with 10 vs 100 vs 1000 real books?
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class RealDataScalabilityBenchmark {

    private static final Pattern WORD_PATTERN = Pattern.compile("[a-z]+");
    private static final String DATALAKE_PATH = "../datalake";

    private Connection connection;
    private Map<String, Set<Integer>> invertedIndex;
    private Gson gson;
    private List<Path> allBookFiles;
    private Path newBookFile;  // A book not in the index yet

    @Param({"10", "50", "100"})  // Start with reasonable sizes
    private int existingBooksCount;

    @Setup(Level.Trial)
    public void setup() throws SQLException, IOException {
        gson = new Gson();

        // Find all real books
        Path datalakePath = Paths.get(DATALAKE_PATH);
        if (!Files.exists(datalakePath)) {
            throw new RuntimeException("Datalake not found! Download books first.");
        }

        try (Stream<Path> paths = Files.walk(datalakePath)) {
            allBookFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".txt"))
                    .collect(Collectors.toList());
        }

        if (allBookFiles.size() < existingBooksCount + 1) {
            throw new RuntimeException(
                    "Not enough books! Need " + (existingBooksCount + 1) +
                            " but found " + allBookFiles.size() + ". Download more books first."
            );
        }

        System.out.println("Found " + allBookFiles.size() + " books");
        System.out.println("Setting up benchmark with " + existingBooksCount + " existing books");

        // Setup database with first N books
        String dbPath = "benchmark_real_" + existingBooksCount + ".db";
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        createSchema();
        populateDatabaseWithRealBooks(existingBooksCount);

        // Build inverted index with first N books
        buildInvertedIndexFromRealBooks(existingBooksCount);

        // Reserve one book for "new book" tests
        newBookFile = allBookFiles.get(existingBooksCount);

        System.out.println("Setup complete: " + existingBooksCount + " books indexed");
    }

    @TearDown(Level.Trial)
    public void tearDown() throws SQLException {
        if (connection != null) connection.close();
    }

    /**
     * Benchmark: Insert metadata for a NEW real book
     */
    @Benchmark
    public void insertNewRealBookMetadata(Blackhole blackhole) throws SQLException, IOException {
        int newBookId = existingBooksCount + 10000;

        // Extract real metadata from the new book
        List<String> lines = Files.readAllLines(newBookFile).stream()
                .limit(100)
                .collect(Collectors.toList());

        Map<String, String> metadata = extractMetadata(lines);

        String sql = "INSERT INTO books (book_id, title, author, language, year, path) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, newBookId);
            stmt.setString(2, metadata.getOrDefault("title", "Unknown"));
            stmt.setString(3, metadata.getOrDefault("author", "Unknown"));
            stmt.setString(4, metadata.getOrDefault("language", "english"));
            stmt.setInt(5, parseYear(metadata.get("year")));
            stmt.setString(6, newBookFile.toString());

            int result = stmt.executeUpdate();
            blackhole.consume(result);

            // Cleanup
            try (Statement cleanup = connection.createStatement()) {
                cleanup.execute("DELETE FROM books WHERE book_id = " + newBookId);
            }
        }
    }

    /**
     * Benchmark: Index a NEW real book (add its words to inverted index)
     */
    @Benchmark
    public void indexNewRealBook(Blackhole blackhole) throws IOException {
        int newBookId = existingBooksCount + 20000;

        // Read and tokenize the new book
        String content = Files.readString(newBookFile);

        Set<String> words = Arrays.stream(content.toLowerCase().split("\\s+"))
                .map(String::trim)
                .filter(word -> WORD_PATTERN.matcher(word).matches())
                .filter(word -> word.length() > 2)
                .collect(Collectors.toSet());

        // Add to inverted index
        for (String word : words) {
            invertedIndex.computeIfAbsent(word, k -> new HashSet<>()).add(newBookId);
        }

        blackhole.consume(words.size());

        // Cleanup: remove the book from index
        for (String word : words) {
            Set<Integer> bookIds = invertedIndex.get(word);
            if (bookIds != null) {
                bookIds.remove(newBookId);
            }
        }
    }

    /**
     * Benchmark: Search existing book by ID
     */
    @Benchmark
    public void searchExistingRealBook(Blackhole blackhole) throws SQLException {
        int searchId = existingBooksCount / 2;

        String sql = "SELECT * FROM books WHERE book_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, searchId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    blackhole.consume(rs.getString("title"));
                }
            }
        }
    }

    /**
     * Benchmark: Search inverted index for common word
     */
    @Benchmark
    public void searchIndexForCommonWord(Blackhole blackhole) {
        // Search for a common word that appears in many books
        Set<Integer> results = invertedIndex.getOrDefault("the", Collections.emptySet());
        blackhole.consume(results);
    }

    /**
     * Benchmark: Serialize inverted index to JSON
     */
    @Benchmark
    public void saveRealIndexToJson(Blackhole blackhole) {
        String json = gson.toJson(invertedIndex);
        blackhole.consume(json.length());
    }

    /**
     * Benchmark: Full-text search by author
     */
    @Benchmark
    public void searchByAuthorInRealBooks(Blackhole blackhole) throws SQLException {
        String sql = "SELECT * FROM books WHERE author LIKE ? LIMIT 10";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, "%a%");  // Any author with 'a'

            List<String> titles = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    titles.add(rs.getString("title"));
                }
            }
            blackhole.consume(titles);
        }
    }

    // ============= Helper Methods =============

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

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_author ON books(author)");
        }
    }

    private void populateDatabaseWithRealBooks(int count) throws SQLException, IOException {
        String sql = "INSERT INTO books (book_id, title, author, language, year, path) VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (int i = 0; i < count; i++) {
                Path bookFile = allBookFiles.get(i);

                // Extract real metadata
                List<String> lines = Files.readAllLines(bookFile).stream()
                        .limit(100)
                        .collect(Collectors.toList());

                Map<String, String> metadata = extractMetadata(lines);

                stmt.setInt(1, i + 1);
                stmt.setString(2, metadata.getOrDefault("title", "Book " + (i + 1)));
                stmt.setString(3, metadata.getOrDefault("author", "Unknown"));
                stmt.setString(4, metadata.getOrDefault("language", "english"));
                stmt.setInt(5, parseYear(metadata.get("year")));
                stmt.setString(6, bookFile.toString());
                stmt.executeUpdate();
            }
        }
    }

    private void buildInvertedIndexFromRealBooks(int count) throws IOException {
        invertedIndex = new HashMap<>();

        for (int i = 0; i < count; i++) {
            Path bookFile = allBookFiles.get(i);
            int bookId = i + 1;

            String content = Files.readString(bookFile);

            // Tokenize
            Set<String> words = Arrays.stream(content.toLowerCase().split("\\s+"))
                    .map(String::trim)
                    .filter(word -> WORD_PATTERN.matcher(word).matches())
                    .filter(word -> word.length() > 2)
                    .collect(Collectors.toSet());

            // Add to index
            for (String word : words) {
                invertedIndex.computeIfAbsent(word, k -> new HashSet<>()).add(bookId);
            }
        }

        System.out.println("Built inverted index: " + invertedIndex.size() + " unique words");
    }

    private Map<String, String> extractMetadata(List<String> lines) {
        Map<String, String> metadata = new HashMap<>();

        for (String line : lines) {
            if (line.contains("Title:")) {
                metadata.put("title", extractValue(line));
            } else if (line.contains("Author:")) {
                metadata.put("author", extractValue(line));
            } else if (line.contains("Language:")) {
                metadata.put("language", extractValue(line));
            } else if (line.contains("Release Date:")) {
                metadata.put("year", extractValue(line));
            }
        }

        return metadata;
    }

    private String extractValue(String line) {
        int colonIndex = line.indexOf(':');
        return colonIndex > 0 ? line.substring(colonIndex + 1).trim() : "";
    }

    private int parseYear(String yearStr) {
        if (yearStr == null) return 2000;

        Pattern yearPattern = Pattern.compile("\\d{4}");
        var matcher = yearPattern.matcher(yearStr);
        return matcher.find() ? Integer.parseInt(matcher.group()) : 2000;
    }
}