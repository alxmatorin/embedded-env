package embeded.postgres.connections;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
class PostgresConnectionTestRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PostgresConnectionTestRunner.class);

    private final DataSource dataSource;
    private final Flyway flyway;
    private final PostgresTestProperties properties;

    PostgresConnectionTestRunner(DataSource dataSource, @Lazy Flyway flyway, PostgresTestProperties properties) {
        this.dataSource = dataSource;
        this.flyway = flyway;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        testConnection();
    }

    private void testConnection() {
        try {
            log.info("postgres: OK — {}", migrateAndExchangeRow());
        } catch (Exception exception) {
            log.error("postgres: FAILED — {}", exception.toString());
        }
    }

    private String migrateAndExchangeRow() throws SQLException {
        flyway.migrate();
        return exchangeRow();
    }

    private String exchangeRow() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String value = insertTestRow(connection);
                verifyTestRow(connection, value);
            } finally {
                connection.rollback();
            }
        }
        return "Flyway migrations applied; row inserted and read; transaction rolled back";
    }

    private String insertTestRow(Connection connection) throws SQLException {
        String value = UUID.randomUUID().toString();
        try (var statement = connection.prepareStatement("insert into connection_test (value) values (?)")) {
            statement.setQueryTimeout(queryTimeoutSeconds());
            statement.setString(1, value);
            statement.executeUpdate();
        }
        return value;
    }

    private int queryTimeoutSeconds() {
        return Math.toIntExact(Math.max(1, properties.timeout().toSeconds()));
    }

    private void verifyTestRow(Connection connection, String expected) throws SQLException {
        try (var statement = connection.prepareStatement("select value from connection_test where value = ?")) {
            statement.setQueryTimeout(queryTimeoutSeconds());
            statement.setString(1, expected);
            try (var rows = statement.executeQuery()) {
                if (!rows.next() || !expected.equals(rows.getString(1))) {
                    throw new IllegalStateException("Postgres returned an unexpected value");
                }
            }
        }
    }
}
