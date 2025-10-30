# Search Engine - Stage 2

A microservices-based search engine for Project Gutenberg books with support for multiple database backends (SQLite, PostgreSQL, MongoDB).

## Table of Contents

- [Project Structure](#project-structure)
- [Architecture Overview](#architecture-overview)
- [Quick Start](#quick-start)
- [Building the Project](#building-the-project)
- [Testing Individual Modules](#testing-individual-modules)
- [Testing with Control Module](#testing-with-control-module)
- [Configuration](#configuration)
- [Database Backends](#database-backends)
- [Troubleshooting](#troubleshooting)
- [Project Cleanup](#project-cleanup)

---

## Project Structure

```
stage2/
├── pom.xml                          # Parent POM
├── ingestion-service/               # Downloads books (Port 7001)
├── indexing-service/                # Builds inverted index (Port 7002)
├── search-service/                  # Provides search API (Port 7003)
├── control-module/                  # Orchestrates all services (Port 7000)
├── datalake/                        # Downloaded books storage
│   └── bucket_*/
│       └── {book_id}.txt
├── datamart/                        # Processed data storage
│   ├── bookdb.sqlite                # SQLite database (default)
│   └── inverted_index.json          # Search index
└── README.md                        # This file
```

---

## Architecture Overview

### Service Ports
| Service | Port | Purpose |
|---------|------|---------|
| Control Module | 7000 | Orchestrates workflows |
| Ingestion Service | 7001 | Downloads books from Project Gutenberg |
| Indexing Service | 7002 | Extracts metadata & builds search index |
| Search Service | 7003 | Provides search API |

### Data Flow
```
Project Gutenberg
       ↓
Ingestion Service (7001)
       ↓
   datalake/
       ↓
Indexing Service (7002)
       ↓
   datamart/ (DB + Index)
       ↓
Search Service (7003)
       ↓
   Search Results
```

### Orchestration Flow
```
Control Module (7000)
       ↓
   [Pipeline]
       ↓
1. Check Services Health
2. Ingest Books → Ingestion Service
3. Build Index → Indexing Service
4. Verify Search → Search Service
```

---

## Quick Start

### Prerequisites

- **Java 17+**
- **Maven 3.8+**
- **PowerShell** (for test scripts, optional)

### 1. Build Everything

```bash
# From the stage2 directory
mvn clean package
```

This builds all four modules and creates executable JAR files.

### 2. Start All Services

Open **4 separate terminals**:

**Terminal 1 - Ingestion Service:**
```bash
cd ingestion-service
java -jar target/ingestion-service-1.0.0.jar
```

**Terminal 2 - Indexing Service:**
```bash
cd indexing-service
java -jar target/indexing-service-1.0.0.jar
```

**Terminal 3 - Search Service:**
```bash
cd search-service
java -jar target/search-service-1.0.0.jar
```

**Terminal 4 - Control Module:**
```bash
cd control-module
java -jar target/control-module-1.0.0.jar
```

### 3. Run Complete Workflow

**Windows (PowerShell):**
```powershell
# Use .exe suffix for curl
curl.exe http://localhost:7000/services/status

curl.exe -X POST http://localhost:7000/pipeline/execute -H "Content-Type: application/json" -d "{\"bookIds\": [11, 1342, 84], \"rebuildIndex\": true}"

curl.exe http://localhost:7000/pipeline/status

curl.exe "http://localhost:7003/search?q=alice"
```

---

## Building the Project

### Build All Modules

```bash
cd stage2
mvn clean package
```

### Build Individual Modules

```bash
# Ingestion Service
cd ingestion-service
mvn clean package

# Indexing Service
cd indexing-service
mvn clean package

# Search Service
cd search-service
mvn clean package

# Control Module
cd control-module
mvn clean package
```

### Run Tests

```bash
# Test all modules
mvn test

# Test individual module
cd ingestion-service
mvn test
```

---

## Testing Individual Modules

Each module can be tested independently without the control module.

### 1. Test Ingestion Service (Port 7001)

**Start the service:**
```bash
cd ingestion-service
java -jar target/ingestion-service-1.0.0.jar
```

**Test commands:**
```bash
# Health check
curl http://localhost:7001/health

# Download a single book (Alice in Wonderland)
curl -X POST http://localhost:7001/ingest/11

# Download another book (Pride and Prejudice)
curl -X POST http://localhost:7001/ingest/1342

# Check status
curl http://localhost:7001/status

# List downloaded books
curl http://localhost:7001/books

# Get book details
curl http://localhost:7001/books/11
```

**Expected results:**
- Books downloaded to `../datalake/bucket_X/` directories
- Health endpoint returns service status
- Status endpoint shows download statistics

---

### 2. Test Indexing Service (Port 7002)

**Prerequisites:** Books must be in datalake (run ingestion first)

**Start the service:**
```bash
cd indexing-service
java -jar target/indexing-service-1.0.0.jar
```

**Test commands:**
```bash
# Health check
curl http://localhost:7002/health

# Index a single book
curl -X POST http://localhost:7002/index/update/11

# Rebuild entire index (indexes all books in datalake)
curl -X POST http://localhost:7002/index/rebuild

# Get index status
curl http://localhost:7002/index/status

# Get statistics
curl http://localhost:7002/stats
```

**Expected results:**
- SQLite database created at `../datamart/bookdb.sqlite`
- Inverted index created at `../datamart/inverted_index.json`
- Metadata extracted and stored in database
- Word-to-document mappings in index

**Verify database:**
```bash
sqlite3 ../datamart/bookdb.sqlite "SELECT * FROM books;"
```

**Verify index:**
```bash
cat ../datamart/inverted_index.json | head -20
```

---

### 3. Test Search Service (Port 7003)

**Prerequisites:** Index must be built (run indexing first)

**Start the service:**
```bash
cd search-service
java -jar target/search-service-1.0.0.jar
```

**Test commands:**
```bash
# Health check
curl http://localhost:7003/health

# Get statistics
curl http://localhost:7003/stats

# Search for "alice"
curl "http://localhost:7003/search?q=alice"

# Search for "pride prejudice"
curl "http://localhost:7003/search?q=pride+prejudice"

# Search with author filter
curl "http://localhost:7003/search?q=alice&author=carroll"

# Search with language filter
curl "http://localhost:7003/search?q=pride&language=english"

# Search with year filter
curl "http://localhost:7003/search?q=alice&year=2008"

# Search with limit
curl "http://localhost:7003/search?q=alice&limit=5"

# Browse all books (no search query)
curl "http://localhost:7003/books?limit=10"
```

**Expected results:**
- Returns JSON with search results
- Results ranked by relevance score
- Filters work correctly
- Statistics show total books and index size

---

### 4. Test Control Module (Port 7000)

**Prerequisites:** All three services must be running

**Start the service:**
```bash
cd control-module
java -jar target/control-module-1.0.0.jar
```

**Test commands:**
```bash
# Health check
curl http://localhost:7000/health

# Check all services status
curl http://localhost:7000/services/status

# Execute pipeline for single book
curl -X POST http://localhost:7000/pipeline/execute \
  -H "Content-Type: application/json" \
  -d '{"bookIds": [11], "rebuildIndex": false}'

# Get pipeline status
curl http://localhost:7000/pipeline/status
```

**Expected results:**
- All services show as "reachable: true"
- Pipeline executes all stages
- Books are downloaded, indexed, and searchable

---

## Testing with Control Module

The control module orchestrates complete workflows automatically.

### Basic Pipeline Execution

**1. Ensure all services are running:**

Check service status:
```bash
curl http://localhost:7000/services/status
```

Expected response:
```json
{
  "timestamp": 1234567890,
  "all_healthy": true,
  "services": {
    "ingestion": {"reachable": true, "status": "running"},
    "indexing": {"reachable": true, "status": "running"},
    "search": {"reachable": true, "status": "running"}
  }
}
```

**2. Execute pipeline:**

**Windows:**
```powershell
curl.exe -X POST http://localhost:7000/pipeline/execute -H "Content-Type: application/json" -d '{"bookIds": [11, 1342, 84], "rebuildIndex": true}'
```

**3. Monitor progress:**

```bash
# Poll every few seconds
curl http://localhost:7000/pipeline/status
```

You'll see stages progress:
- `verification` → Checking services
- `ingestion` → Downloading books
- `indexing` → Building index
- `verification` → Verifying search
- `completed` → Done!

**4. Verify results:**

```bash
# Check search statistics
curl http://localhost:7003/stats

# Test search
curl "http://localhost:7003/search?q=alice"
```

---

### Batch Processing

**Process multiple books from a file:**

**1. Create book list (books.txt):**
```
11
84
1342
1661
2701
```

**2. Run batch processing:**
```powershell
Invoke-WebRequest -Uri http://localhost:7000/pipeline/execute -Method POST -Body (@{bookIds=1..1000;rebuildIndex=$true}|ConvertTo-Json) -ContentType "application/json"
```


The script will:
- Execute pipeline for all books
- Monitor progress

---

### Pipeline Options

**Option 1: Index new books only (faster):**
```json
{
  "bookIds": [98, 120, 768],
  "rebuildIndex": false
}
```

**Option 2: Rebuild entire index (thorough):**
```json
{
  "bookIds": [11, 1342, 84],
  "rebuildIndex": true
}
```

**When to use `rebuildIndex: true`:**
- First time running the system
- After making changes to indexing logic
- Want to ensure index consistency
- Don't mind waiting longer

**When to use `rebuildIndex: false`:**
- Adding new books to existing collection
- Want faster processing
- Index is already built and working

---

## Configuration

### Default Configuration

All services use sensible defaults:

| Setting | Default Value |
|---------|--------------|
| Database Type | SQLite |
| Database Path | ../datamart/bookdb.sqlite |
| Index Type | JSON |
| Index Path | ../datamart/inverted_index.json |
| Datalake Path | ../datalake |
| Bucket Size | 10 books per bucket |

### Command-Line Configuration

**Change database type:**
```bash
# Use PostgreSQL
java -jar target/indexing-service-1.0.0.jar --db.type postgresql

# Use MongoDB
java -jar target/indexing-service-1.0.0.jar --db.type mongodb
```

**Change ports:**
```bash
java -jar target/ingestion-service-1.0.0.jar --server.port 8001
java -jar target/indexing-service-1.0.0.jar --server.port 8002
java -jar target/search-service-1.0.0.jar --server.port 8003
java -jar target/control-module-1.0.0.jar --server.port 8000
```

**Change paths:**
```bash
java -jar target/ingestion-service-1.0.0.jar --datalake.path /custom/path/datalake

java -jar target/indexing-service-1.0.0.jar \
  --datalake.path /custom/path/datalake \
  --datamart.path /custom/path/datamart
```

### Configuration Files

Each service has `src/main/resources/application.properties`:

**Example: indexing-service/application.properties**
```properties
server.port=7002
db.type=sqlite
db.sqlite.path=../datamart/bookdb.sqlite
datalake.path=../datalake
datamart.path=../datamart
index.type=json
index.filename=inverted_index.json
```

---

## Database Backends

The system supports three database backends:

### 1. SQLite (Default - Recommended)

**Advantages:**
- No setup required
- Zero configuration
- File-based, portable
- Perfect for development/testing
- Fast for small to medium collections

**Run with SQLite:**
```bash
java -jar target/indexing-service-1.0.0.jar
java -jar target/search-service-1.0.0.jar
```

Database file: `datamart/bookdb.sqlite`

**Query database:**
```bash
sqlite3 datamart/bookdb.sqlite
sqlite> SELECT * FROM books;
sqlite> .quit
```

---

### 2. PostgreSQL

**Advantages:**
- Better for large collections (10,000+ books)
- Better concurrent access
- Advanced query features
- Production-ready

**Setup PostgreSQL:**

1. Install PostgreSQL
2. Create database:
```bash
createdb bookdb
```

Or using psql:
```sql
psql -U postgres
CREATE DATABASE bookdb;
\q
```

3. Update configuration:

Edit `application.properties`:
```properties
db.type=postgresql
db.postgresql.url=jdbc:postgresql://localhost:5432/bookdb
db.postgresql.username=postgres
db.postgresql.password=your_password
```

Or use command-line:
```bash
java -jar target/indexing-service-1.0.0.jar \
  --db.type postgresql \
  --db.postgresql.password your_password

java -jar target/search-service-1.0.0.jar \
  --db.type postgresql \
  --db.postgresql.password your_password
```

**Query database:**
```bash
psql -U postgres -d bookdb
SELECT * FROM books;
\q
```

---

### 3. MongoDB

**Advantages:**
- Document-oriented (flexible schema)
- Horizontal scalability
- Good for unstructured data

**Setup MongoDB:**

1. Install MongoDB
2. Start MongoDB:
```bash
mongod
```

3. Run services:
```bash
java -jar target/indexing-service-1.0.0.jar --db.type mongodb
java -jar target/search-service-1.0.0.jar --db.type mongodb
```

**Query database:**
```bash
mongosh
use bookdb
db.books.find()
exit
```

---

### Switching Databases

**Important:** When switching database backends, you need to:

1. Stop all services
2. Update both indexing and search services to use same database
3. Rebuild the index

**Example - Switch from SQLite to PostgreSQL:**

```bash
# Stop all services (Ctrl+C in each terminal)

# Start PostgreSQL database
createdb bookdb

# Start indexing service with PostgreSQL
cd indexing-service
java -jar target/indexing-service-1.0.0.jar --db.type postgresql

# Start search service with PostgreSQL
cd search-service
java -jar target/search-service-1.0.0.jar --db.type postgresql

# Rebuild index
curl -X POST http://localhost:7002/index/rebuild
```

---

## Troubleshooting

### Issue: "Port already in use"

**Symptom:**
```
Address already in use: bind
```

**Solution:**
```bash
# Find process using the port (Windows)
netstat -ano | findstr :7001

# Kill the process
taskkill /PID <process_id> /F

# Or use a different port
java -jar target/ingestion-service-1.0.0.jar --server.port 8001
```

---

### Issue: "Service not responding" or "Service unreachable"

**Symptom:**
Control module reports services as unreachable.

**Solution:**

1. Check each service is actually running
2. Verify ports are correct:
```bash
curl http://localhost:7001/health
curl http://localhost:7002/health
curl http://localhost:7003/health
```

3. Check firewall settings
4. Review service logs for errors

---

### Issue: "Index file not found"

**Symptom:**
Search service can't find index.

**Solution:**
```bash
# Rebuild the index
curl -X POST http://localhost:7002/index/rebuild

# Check file exists
ls ../datamart/inverted_index.json

# Restart search service
```

---

### Issue: "No books found in datalake"

**Symptom:**
Indexing service reports no books.

**Solution:**
```bash
# Download some books first
curl -X POST http://localhost:7001/ingest/11
curl -X POST http://localhost:7001/ingest/1342

# Check datalake directory
ls ../datalake/bucket_*/

# Then rebuild index
curl -X POST http://localhost:7002/index/rebuild
```

---

### Issue: "Database connection failed" (PostgreSQL)

**Symptom:**
```
Connection refused: connect
```

**Solution:**
1. Verify PostgreSQL is running:
```bash
pg_isready
```

2. Check database exists:
```bash
psql -U postgres -l
```

3. Verify credentials in application.properties

4. Create database if needed:
```bash
createdb bookdb
```

---

### Issue: "Search returns no results"

**Symptom:**
All searches return empty results.

**Solution:**

1. Check index is loaded:
```bash
curl http://localhost:7003/stats
```

2. Verify books are in database:
```bash
sqlite3 datamart/bookdb.sqlite "SELECT COUNT(*) FROM books;"
```

3. Check index file exists and has content:
```bash
ls -lh ../datamart/inverted_index.json
cat ../datamart/inverted_index.json | head
```

4. Rebuild index:
```bash
curl -X POST http://localhost:7002/index/rebuild
```

5. Restart search service

---

## Project Cleanup

### Clean Build Artifacts

```bash
# Clean all modules
mvn clean

# Remove target directories
find . -type d -name "target" -exec rm -rf {} +
```

### Clean Generated Data

```bash
# Remove datalake (downloaded books)
rm -rf datalake/

# Remove datamart (database and index)
rm -rf datamart/

# Remove unwanted external directories
rm -rf transactions/
rm -rf test-*/
rm derby.log
```

### Reset Everything

**Complete cleanup and fresh start:**

```bash
# 1. Stop all running services (Ctrl+C in each terminal)

# 2. Clean build artifacts
cd stage2
mvn clean

# 3. Remove generated data
rm -rf datalake/
rm -rf datamart/

# 4. Rebuild
mvn clean package

# 5. Start fresh
# Now start services again
```

---

## Performance Tips

### For Better Performance

1. **Use batch operations** - Process multiple books in one pipeline execution
2. **Use `rebuildIndex: false`** when adding new books to existing collection
3. **Use SQLite for < 1,000 books** - Faster for small collections
4. **Use PostgreSQL for > 10,000 books** - Better for large collections
5. **Increase heap size** for large collections:
```bash
java -Xmx2g -jar target/indexing-service-1.0.0.jar
```

### Expected Performance

| Operation | Time (approx.) |
|-----------|----------------|
| Download 1 book | 2-5 seconds |
| Index 1 book | 1-2 seconds |
| Rebuild index (10 books) | 10-15 seconds |
| Rebuild index (100 books) | 1-2 minutes |
| Search query | < 100ms |

---

## Summary

### Testing Individual Modules
- Each module can run and be tested independently

### Testing with Control Module
- Control module orchestrates complete workflows
- All services must be running
- Use `/pipeline/execute` for automated workflows
- Monitor progress with `/pipeline/status`

### Key Points
- SQLite is the default (zero configuration)
- All services run on different ports (7000-7003)
- Control module provides the easiest way to test everything
- Individual module testing gives more control and debugging

---

## Getting Help

### Check Service Logs
Each service outputs logs to the console. Look for:
- `ERROR` - Something failed
- `WARN` - Potential issues
- `INFO` - Normal operation

### Debug Mode
Add `-Dlog.level=DEBUG` for more verbose output:
```bash
java -Dlog.level=DEBUG -jar target/ingestion-service-1.0.0.jar
```

### Health Checks
Always start by checking health endpoints:
```bash
curl http://localhost:7001/health  # Ingestion
curl http://localhost:7002/health  # Indexing
curl http://localhost:7003/health  # Search
curl http://localhost:7000/health  # Control
```

### Common Issues Summary

| Issue | Quick Fix |
|-------|-----------|
| Port in use | Use different port: `--server.port 8001` |
| Service unreachable | Check service is running: `curl .../health` |
| No search results | Rebuild index: `POST /index/rebuild` |
| Database error | Check database is running and accessible |
| External directories | Add to `.gitignore`, safe to delete |

---
