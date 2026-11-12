package embeded.postgres.connections;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class PostgresConnectionTestRunnerTest {

    private final DataSource dataSource = mock(DataSource.class);
    private final Flyway flyway = mock(Flyway.class);
    private final Connection connection = mock(Connection.class);
    private final PreparedStatement insert = mock(PreparedStatement.class);
    private final PreparedStatement select = mock(PreparedStatement.class);
    private final ResultSet rows = mock(ResultSet.class);
    private final PostgresTestProperties properties = new PostgresTestProperties(Duration.ofSeconds(1));
    private final PostgresConnectionTestRunner runner = new PostgresConnectionTestRunner(
            dataSource, flyway, properties);

    @BeforeEach
    void configureDatabase() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("insert into connection_test (value) values (?)")).thenReturn(insert);
        when(connection.prepareStatement("select value from connection_test where value = ?")).thenReturn(select);
        when(select.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true);
    }

    @Test
    void writesAndReadsRowThenRollsBackAndClosesConnection(CapturedOutput output) throws SQLException {
        var writtenValue = new AtomicReference<String>();
        doAnswer(invocation -> {
            writtenValue.set(invocation.getArgument(1));
            return null;
        }).when(insert).setString(eq(1), anyString());
        when(rows.getString(1)).thenAnswer(invocation -> writtenValue.get());

        runner.run(new DefaultApplicationArguments());

        assertThat(output).contains("postgres: OK");
        var order = inOrder(flyway, dataSource, insert);
        order.verify(flyway).migrate();
        order.verify(dataSource).getConnection();
        order.verify(insert).executeUpdate();
        verify(connection).setAutoCommit(false);
        verify(select).setString(1, writtenValue.get());
        verify(connection).rollback();
        verify(connection).close();
    }

    @Test
    void reportsUnexpectedRowAndStillRollsBackAndClosesConnection(CapturedOutput output) throws SQLException {
        when(rows.getString(1)).thenReturn("unexpected");

        runner.run(new DefaultApplicationArguments());

        assertThat(output).contains("postgres: FAILED");
        assertThat(output).contains("Postgres returned an unexpected value");
        verify(connection).rollback();
        verify(connection).close();
    }

    @Test
    void reportsConnectionFailure(CapturedOutput output) throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("database unavailable"));

        runner.run(new DefaultApplicationArguments());

        assertThat(output).contains("postgres: FAILED");
        assertThat(output).contains("database unavailable");
    }

    @Test
    void reportsMigrationFailureWithoutRunningDataExchange(CapturedOutput output) throws SQLException {
        when(flyway.migrate()).thenThrow(new FlywayException("migration failed"));

        runner.run(new DefaultApplicationArguments());

        assertThat(output).contains("postgres: FAILED");
        assertThat(output).contains("migration failed");
        verify(dataSource, never()).getConnection();
    }
}
