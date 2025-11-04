package org.labubus.benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Microbenchmarks using REAL books from datalake
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
public class RealDataBenchmark {

    private static final Pattern WORD_PATTERN = Pattern.compile("[a-z]+");
    private static final String DATALAKE_PATH = "../datalake";

    private List<Path> bookFiles;
    private String sampleBookContent;
    private List<String> sampleBookLines;

    @Setup
    public void setup() throws IOException {
        // Find all book files in datalake
        Path datalakePath = Paths.get(DATALAKE_PATH);

        if (!Files.exists(datalakePath)) {
            throw new RuntimeException("Datalake not found at: " + datalakePath.toAbsolutePath());
        }

        try (Stream<Path> paths = Files.walk(datalakePath)) {
            bookFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".txt"))
                    .limit(100)  // Use first 100 books
                    .collect(Collectors.toList());
        }

        if (bookFiles.isEmpty()) {
            throw new RuntimeException("No books found in datalake! Download books first.");
        }

        System.out.println("Found " + bookFiles.size() + " books for benchmarking");

        // Load one sample book for single-book tests
        sampleBookContent = Files.readString(bookFiles.get(0));
        sampleBookLines = Files.readAllLines(bookFiles.get(0));

        System.out.println("Sample book size: " + sampleBookContent.length() + " characters");
    }

    /**
     * Benchmark: Tokenize a single real book
     */
    @Benchmark
    public void tokenizeSingleBook(Blackhole blackhole) {
        Set<String> words = Arrays.stream(sampleBookContent.toLowerCase().split("\\s+"))
                .map(String::trim)
                .filter(word -> WORD_PATTERN.matcher(word).matches())
                .filter(word -> word.length() > 2)
                .collect(Collectors.toSet());

        blackhole.consume(words);
    }

    /**
     * Benchmark: Extract metadata from real book header
     */
    @Benchmark
    public void extractMetadataFromRealBook(Blackhole blackhole) {
        Map<String, String> metadata = new HashMap<>();

        // Read first 100 lines (header section)
        List<String> headerLines = sampleBookLines.stream()
                .limit(100)
                .collect(Collectors.toList());

        for (String line : headerLines) {
            if (line.contains("Title:")) {
                metadata.put("title", extractValue(line));
            } else if (line.contains("Author:")) {
                metadata.put("author", extractValue(line));
            } else if (line.contains("Language:")) {
                metadata.put("language", extractValue(line));
            } else if (line.contains("Release Date:")) {
                metadata.put("year", extractYear(line));
            }
        }

        blackhole.consume(metadata);
    }

    /**
     * Benchmark: Process multiple real books (inverted index building)
     */
    @Benchmark
    public void buildInvertedIndexFromRealBooks(Blackhole blackhole) throws IOException {
        Map<String, Set<Integer>> invertedIndex = new HashMap<>();

        // Process first 10 books
        for (int i = 0; i < Math.min(10, bookFiles.size()); i++) {
            Path bookFile = bookFiles.get(i);
            int bookId = i + 1;

            String content = Files.readString(bookFile);

            // Extract words
            Set<String> words = Arrays.stream(content.toLowerCase().split("\\s+"))
                    .map(String::trim)
                    .filter(word -> WORD_PATTERN.matcher(word).matches())
                    .filter(word -> word.length() > 2)
                    .collect(Collectors.toSet());

            // Add to inverted index
            for (String word : words) {
                invertedIndex.computeIfAbsent(word, k -> new HashSet<>()).add(bookId);
            }
        }

        blackhole.consume(invertedIndex);
    }

    /**
     * Benchmark: Read and parse all available real books
     */
    @Benchmark
    public void readAllRealBooks(Blackhole blackhole) throws IOException {
        int totalWords = 0;

        for (Path bookFile : bookFiles) {
            String content = Files.readString(bookFile);
            totalWords += content.split("\\s+").length;
        }

        blackhole.consume(totalWords);
    }

    /**
     * Benchmark: Extract words from real book (skip header)
     */
    @Benchmark
    public void extractWordsFromRealBook(Blackhole blackhole) {
        // Skip first 100 lines (header)
        Set<String> words = sampleBookLines.stream()
                .skip(100)
                .flatMap(line -> Arrays.stream(line.toLowerCase().split("\\s+")))
                .map(String::trim)
                .filter(word -> WORD_PATTERN.matcher(word).matches())
                .filter(word -> word.length() > 2)
                .collect(Collectors.toSet());

        blackhole.consume(words);
    }

    // Helper methods
    private String extractValue(String line) {
        int colonIndex = line.indexOf(':');
        return colonIndex > 0 ? line.substring(colonIndex + 1).trim() : "";
    }

    private String extractYear(String line) {
        Pattern yearPattern = Pattern.compile("\\d{4}");
        var matcher = yearPattern.matcher(line);
        return matcher.find() ? matcher.group() : "";
    }
}