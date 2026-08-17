package com.audioagent.file.migration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledIfSystemProperty(
        named = "audioagent.migration.mysql.enabled", matches = "true")
class AudioFileVersionNumberingMigrationTest {

    private static final String EXTERNAL_JDBC_URL_PROPERTY =
            "audioagent.migration.mysql.jdbc-url";
    private static MySQLContainer<?> mysql;
    private static String jdbcUrl;
    private static String username;
    private static String password;

    private Path migrationPath;

    @BeforeAll
    static void startMySql() {
        jdbcUrl = System.getProperty(EXTERNAL_JDBC_URL_PROPERTY);
        if (jdbcUrl != null && !jdbcUrl.isBlank()) {
            username = System.getProperty(
                    "audioagent.migration.mysql.username", "root");
            password = System.getProperty(
                    "audioagent.migration.mysql.password", "");
            return;
        }
        mysql = new MySQLContainer<>(
                DockerImageName.parse("mysql:8.0.36"))
                .withDatabaseName("audio_agent")
                .withUsername("audio_agent_test")
                .withPassword("audio_agent_test");
        mysql.start();
        jdbcUrl = mysql.getJdbcUrl();
        username = mysql.getUsername();
        password = mysql.getPassword();
    }

    @AfterAll
    static void stopMySql() {
        if (mysql != null) {
            mysql.stop();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        migrationPath = migrationPath();
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS audio_file");
            statement.execute("""
                    CREATE TABLE audio_file (
                        id BIGINT UNSIGNED PRIMARY KEY,
                        source_file_id BIGINT UNSIGNED NULL,
                        root_audio_file_id BIGINT UNSIGNED NULL,
                        source_execution_id BIGINT NULL,
                        version_no INT NOT NULL,
                        created_at DATETIME NOT NULL
                    ) ENGINE = InnoDB
                    """);
            statement.executeUpdate("""
                    INSERT INTO audio_file
                        (id, source_file_id, root_audio_file_id,
                         source_execution_id, version_no, created_at)
                    VALUES
                        (103, 101, 100, 903, 2, '2026-08-13 10:03:00'),
                        (102, 100, 100, 902, 1, '2026-08-13 10:01:00'),
                        (100, NULL, 100, NULL, 0, '2026-08-13 10:05:00'),
                        (201, 200, 200, 904, 1, '2026-08-13 11:01:00'),
                        (101, 100, 100, 901, 1, '2026-08-13 10:01:00'),
                        (200, NULL, 200, NULL, 0, '2026-08-13 11:05:00')
                    """);
        }
    }

    @Test
    void completeMigrationCreatesAndEnforcesIdempotentUniqueIndex()
            throws Exception {
        executeMigration();

        assertEquals(Map.of(100L, 0, 101L, 1, 102L, 2, 103L, 3),
                versions(100L));
        assertEquals(Map.of(200L, 0, 201L, 1), versions(200L));
        assertEquals(List.of(
                        new IndexColumn(0, 1, "root_audio_file_id"),
                        new IndexColumn(0, 2, "version_no")),
                indexColumns());

        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            assertThrows(SQLException.class, () -> statement.executeUpdate("""
                    INSERT INTO audio_file
                        (id, source_file_id, root_audio_file_id,
                         source_execution_id, version_no, created_at)
                    VALUES (104, 100, 100, 905, 2,
                            '2026-08-13 10:04:00')
                    """));

            assertEquals(2, statement.executeUpdate("""
                    INSERT INTO audio_file
                        (id, source_file_id, root_audio_file_id,
                         source_execution_id, version_no, created_at)
                    VALUES
                        (300, NULL, 300, NULL, 0,
                         '2026-08-13 12:05:00'),
                        (301, 300, 300, 906, 2,
                         '2026-08-13 12:01:00')
                    """));
        }

        executeMigration();

        assertEquals(List.of(
                        new IndexColumn(0, 1, "root_audio_file_id"),
                        new IndexColumn(0, 2, "version_no")),
                indexColumns());
        assertEquals(Map.of(300L, 0, 301L, 1), versions(300L));
    }

    private void executeMigration() throws Exception {
        String script = Files.readString(migrationPath);
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            for (String sql : splitStatements(script)) {
                statement.execute(sql);
                while (statement.getMoreResults()
                        || statement.getUpdateCount() != -1) {
                    // Consume every result produced by CALL before continuing.
                }
            }
        }
    }

    private List<String> splitStatements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String delimiter = ";";
        for (String line : script.split("\\R")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            if (trimmed.startsWith("DELIMITER ")) {
                if (!current.toString().isBlank()) {
                    throw new IllegalArgumentException(
                            "DELIMITER changed inside an SQL statement");
                }
                delimiter = trimmed.substring("DELIMITER ".length())
                        .strip();
                continue;
            }
            current.append(line).append('\n');
            if (trimmed.endsWith(delimiter)) {
                String sql = current.toString().strip();
                statements.add(sql.substring(
                        0, sql.length() - delimiter.length()));
                current.setLength(0);
            }
        }
        if (!current.toString().isBlank()) {
            throw new IllegalArgumentException(
                    "Migration contains an unterminated SQL statement");
        }
        return statements;
    }

    private Map<Long, Integer> versions(long rootId) throws Exception {
        var result = new java.util.LinkedHashMap<Long, Integer>();
        try (Connection connection = connection();
             var statement = connection.prepareStatement("""
                     SELECT id, version_no
                     FROM audio_file
                     WHERE root_audio_file_id = ?
                     ORDER BY id
                     """)) {
            statement.setLong(1, rootId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.put(rows.getLong("id"),
                            rows.getInt("version_no"));
                }
            }
        }
        return result;
    }

    private List<IndexColumn> indexColumns() throws Exception {
        List<IndexColumn> result = new ArrayList<>();
        try (Connection connection = connection();
             var statement = connection.prepareStatement("""
                     SELECT non_unique, seq_in_index, column_name
                     FROM information_schema.statistics
                     WHERE table_schema = 'audio_agent'
                       AND table_name = 'audio_file'
                       AND index_name = 'uk_audio_file_root_version'
                     ORDER BY seq_in_index
                     """)) {
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new IndexColumn(
                            rows.getInt("non_unique"),
                            rows.getInt("seq_in_index"),
                            rows.getString("column_name")));
                }
            }
        }
        return result;
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl, username, password);
    }

    private Path migrationPath() {
        Path modulePath = Path.of("sql",
                "24_fix_audio_file_version_numbering.sql");
        if (Files.exists(modulePath)) {
            return modulePath;
        }
        return Path.of("backend").resolve(modulePath);
    }

    private record IndexColumn(int nonUnique, int sequence,
                               String columnName) {
    }
}
