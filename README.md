# Search Engine - Stage 2 (Basic Guide)

A small microservices project that downloads books from Project Gutenberg, builds a search index, and lets you query it. There are four services that talk to each other over HTTP.

## Requirements

- Java 17 or newer
- Maven 3.8+ (to build)
- Windows PowerShell with curl.exe (built-in on Windows 10/11)

## What’s in the folder

- `ingestion-service/` (port 7001): downloads books into `datalake/`
- `indexing-service/` (port 7002): reads books from `datalake/`, writes index and metadata to `datamart/`
- `search-service/` (port 7003): searches the index and returns results
- `control-module/` (port 7000): simple orchestrator that calls the three services
- `datalake/`: raw downloaded books, grouped in `bucket_*` folders
- `datamart/`: processed data
  - `bookdb.sqlite` (default DB)
  - `inverted_index.json` (search index)

Parent `pom.xml` builds everything; each service also has its own `pom.xml`.

## Build (one command)

From the `stage2` folder:

```powershell
mvn clean package
```

This compiles all modules and creates runnable JARs in each `target/` folder.

## Run the services (4 terminals)

Open four PowerShell windows and run one service per window:

```powershell
# 1) Ingestion (7001)
cd ingestion-service
java -jar target/ingestion-service-1.0.0.jar
```

```powershell
# 2) Indexing (7002)
cd indexing-service
java -jar target/indexing-service-1.0.0.jar
```

```powershell
# 3) Search (7003)
cd search-service
java -jar target/search-service-1.0.0.jar
```

```powershell
# 4) Control (7000)
cd control-module
java -jar target/control-module-1.0.0.jar
```

Stop a service with Ctrl+C in its window.

## Try the full workflow (end-to-end)

Use the control module to check health, run the pipeline, and search:

```powershell
# 1) Check all services status
curl.exe http://localhost:7000/services/status

# 2) Ingest one book and rebuild the index once
curl.exe -X POST http://localhost:7000/pipeline/execute -H "Content-Type: application/json" -d '{\"bookIds\": [11], \"rebuildIndex\": true}'
# 3) Monitor pipeline status
curl.exe http://localhost:7000/pipeline/status

# 4) Search for a word
curl.exe "http://localhost:7003/search?q=alice"
```

## Use services directly (optional)

Ingestion (7001):

```powershell
curl.exe http://localhost:7001/health
curl.exe -X POST http://localhost:7001/ingest/11
curl.exe http://localhost:7001/status
```

Indexing (7002):

```powershell
curl.exe http://localhost:7002/health
curl.exe -X POST http://localhost:7002/index/rebuild
curl.exe http://localhost:7002/index/status
```

Search (7003):

```powershell
curl.exe http://localhost:7003/health
curl.exe "http://localhost:7003/search?q=alice"
```

## Configuration (simple)

- Each service reads `src/main/resources/application.properties`.
- Defaults work out of the box: ports 7000–7003, `datalake/` and `datamart/` under the project.
- You can override at startup, e.g.:

```powershell
java -jar target/ingestion-service-1.0.0.jar --server.port=8001 --datalake.path=../datalake
java -jar target/indexing-service-1.0.0.jar --server.port=8002 --datalake.path=../datalake --datamart.path=../datamart
java -jar target/search-service-1.0.0.jar   --server.port=8003
java -jar target/control-module-1.0.0.jar   --server.port=8000
```

SQLite is the default metadata database. The inverted index is a JSON file (`datamart/inverted_index.json`).

## Where files go

- Downloads: `datalake/bucket_*/{book_id}.txt`
- Database: `datamart/bookdb.sqlite` (created by indexing)
- Index: `datamart/inverted_index.json`

## Troubleshooting

- If a port is busy, pick another with `--server.port=...`.
- First run may take a while while books download and the index builds.
- On Windows, use `curl.exe` in PowerShell to avoid alias issues with `curl`.
- To reset data, stop services and delete `datalake/` and `datamart/`.

## Clean up

```powershell
mvn clean
```

That removes build artifacts (the downloaded/indexed data folders stay until you delete them).
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



## Troubleshooting

### Issue: "Port already in use"

**Symptom:**
```
Address already in use: XYZ
```

**Solution:**
```bash
# Use a different port
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
# Download a book first
curl -X POST http://localhost:7001/ingest/11

# Check datalake directory
ls ../datalake/bucket_*/

# Then rebuild index
curl -X POST http://localhost:7002/index/rebuild
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
4. **Increase heap size** for large collections:
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
