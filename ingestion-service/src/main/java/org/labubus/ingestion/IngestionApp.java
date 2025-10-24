package org.labubus.ingestion;

import io.javalin.Javalin;
import org.labubus.ingestion.controller.IngestionController;
import org.labubus.ingestion.service.BookIngestionService;
import org.labubus.ingestion.storage.DatalakeStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class IngestionApp {
	private static final Logger logger = LoggerFactory.getLogger(IngestionApp.class);

	public static void main(String[] args) {
		try {
			// Load configuration
			Properties config = loadConfiguration();

			// Extract configuration values
			int port = Integer.parseInt(config.getProperty("server.port", "7001"));
			String datalakePath = config.getProperty("datalake.path", "../datalake");
			int bucketSize = Integer.parseInt(config.getProperty("datalake.bucket.size", "10"));
			String gutenbergUrl = config.getProperty("gutenberg.base.url",
					"https://www.gutenberg.org/cache/epub");
			int timeout = Integer.parseInt(config.getProperty("gutenberg.download.timeout", "30000"));

			logger.info("Starting Ingestion Service...");
			logger.info("Configuration:");
			logger.info("  Port: {}", port);
			logger.info("  Datalake Path: {}", datalakePath);
			logger.info("  Bucket Size: {}", bucketSize);
			logger.info("  Gutenberg URL: {}", gutenbergUrl);
			logger.info("  Timeout: {}ms", timeout);

			// Initialize components
			DatalakeStorage storage = new DatalakeStorage(datalakePath, bucketSize);
			BookIngestionService ingestionService = new BookIngestionService(
					storage,
					gutenbergUrl,
					timeout
			);
			IngestionController controller = new IngestionController(ingestionService, storage);

			// Create and configure Javalin app
			Javalin app = Javalin.create(javalinConfig -> {
				javalinConfig.http.defaultContentType = "application/json";
				javalinConfig.showJavalinBanner = false;
			}).start(port);

			// Register routes
			controller.registerRoutes(app);

			// Add shutdown hook
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				logger.info("Shutting down Ingestion Service...");
				app.stop();
				logger.info("Ingestion Service stopped");
			}));

			logger.info("✅ Ingestion Service started successfully on port {}", port);
			logger.info("📚 Ready to ingest books!");
			logger.info("Try: POST http://localhost:{}/ingest/11", port);

		} catch (Exception e) {
			logger.error("Failed to start Ingestion Service", e);
			System.exit(1);
		}
	}

	/**
	 * Load configuration from application.properties
	 */
	private static Properties loadConfiguration() {
		Properties properties = new Properties();

		// Try to load from classpath
		try (InputStream input = IngestionApp.class.getClassLoader()
				.getResourceAsStream("application.properties")) {

			if (input == null) {
				logger.warn("application.properties not found, using defaults");
				return properties;
			}

			properties.load(input);
			logger.info("Loaded configuration from application.properties");

		} catch (IOException e) {
			logger.warn("Failed to load application.properties, using defaults", e);
		}

		return properties;
	}
}