package com.example.dreamdiary.config;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DatabaseDirectoryInitializer {

    private static final Logger log = LoggerFactory.getLogger(DatabaseDirectoryInitializer.class);

    private final String sqliteDbPath;

    public DatabaseDirectoryInitializer(@Value("${app.sqlite.db-path}") String sqliteDbPath) {
        this.sqliteDbPath = sqliteDbPath;
    }

    @PostConstruct
    public void initialize() {
        if (!StringUtils.hasText(sqliteDbPath)) {
            throw new IllegalStateException("SQLITE_DB_PATH must not be blank.");
        }
        if (":memory:".equalsIgnoreCase(sqliteDbPath)) {
            return;
        }

        Path dbPath = Paths.get(sqliteDbPath).toAbsolutePath().normalize();
        Path parent = dbPath.getParent();
        if (parent != null && Files.notExists(parent)) {
            try {
                Files.createDirectories(parent);
                log.info("Created SQLite directory: {}", parent);
            } catch (IOException exception) {
                throw new IllegalStateException(
                        "Could not create SQLite directory for path: " + sqliteDbPath,
                        exception
                );
            }
        }
    }
}
