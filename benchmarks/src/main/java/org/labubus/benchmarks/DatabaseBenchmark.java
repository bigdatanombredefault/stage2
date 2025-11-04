package org.labubus.benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class DatabaseBenchmark {

    private Connection sqliteConn;
    private Connection postgresConn;

    @Param({"SQLite", "PostgreSQL"})
    private String databaseType;

    @Setup(Level.Trial)
    public void setup() throws SQLException {
        if ("SQLite".equals(databaseType)) {
            setupSQLite();
        } else if ("PostgreSQL".equals(databaseType)) {
            setupPostgreSQL();
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() throws SQLException {
        if (sqliteConn != null) sqliteConn.close();
        if (postgresConn != null) postgresConn.close();
    }

    @Benchmark
    public void insertBook(Blackhole blackhole) throws SQLException {
        Connection conn = getConnection();
        String sql = "INSERT INTO books (book_id, title, author, language, year, path) VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            int bookId = (int) (Math.random() * 100000);
            stmt.setInt(1, bookId);
            stmt.setString(2, "Test Book " + bookId);
            stmt.setString(3, "Test Author");
            stmt.setString(4, "english");
            stmt.setInt(5, 2024);
            stmt.setString(6, "bucket_1/" + bookId + ".txt");

            int result = stmt.executeUpdate();
            blackhole.consume(result);
        }
    }

    @Benchmark
    public void selectBookById(Blackhole blackhole) throws SQLException {
        Connection conn = getConnection();
        String sql = "SELECT * FROM books WHERE book_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, 1);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    blackhole.consume(rs.getString("title"));
                }
            }
        }
    }

    @Benchmark
    public void selectAllBooks(Blackhole blackhole) throws SQLException {
        Connection conn = getConnection();
        String sql = "SELECT * FROM books LIMIT 100";

        List<String> titles = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                titles.add(rs.getString("title"));
            }
        }
        blackhole.consume(titles);
    }

    private Connection getConnection() {
        return "SQLite".equals(databaseType) ? sqliteConn : postgresConn;
    }

    private void setupSQLite() throws SQLException {
        sqliteConn = DriverManager.getConnection("jdbc:sqlite:benchmark.db");
        createTable(sqliteConn);
        insertSampleData(sqliteConn);
    }

    private void setupPostgreSQL() throws SQLException {
        try {
            String url = "jdbc:postgresql://localhost:5432/benchmark_db";
            postgresConn = DriverManager.getConnection(url, "postgres", "postgres");
            createTable(postgresConn);
            insertSampleData(postgresConn);
        } catch (SQLException e) {
            System.err.println("PostgreSQL unavailable, using SQLite: " + e.getMessage());
            postgresConn = sqliteConn;
        }
    }

    private void createTable(Connection conn) throws SQLException {
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
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private void insertSampleData(Connection conn) throws SQLException {
        String sql = "INSERT OR REPLACE INTO books VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 1; i <= 100; i++) {
                stmt.setInt(1, i);
                stmt.setString(2, "Book " + i);
                stmt.setString(3, "Author " + (i % 10));
                stmt.setString(4, "english");
                stmt.setInt(5, 2000 + (i % 25));
                stmt.setString(6, "bucket_" + (i / 10) + "/" + i + ".txt");
                stmt.executeUpdate();
            }
        }
    }
}