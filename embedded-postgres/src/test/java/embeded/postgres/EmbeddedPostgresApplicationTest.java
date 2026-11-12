package embeded.postgres;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddedPostgresApplicationTest {

    @TempDir
    Path dataDirectory;

    @TempDir
    Path processLogs;

    @Test
    @Timeout(90)
    void servesJdbcClientsAndPreservesDataAfterRestart() throws Exception {
        int port = availablePort();
        String url = "jdbc:postgresql://127.0.0.1:" + port + "/postgres";
        try (var application = startApplication(port)) {
            insertRow(url);
        }
        try (var application = startApplication(port)) {
            verifyRow(url);
        }
    }

    @Test
    @Timeout(90)
    void preservesCommittedRowsAfterPostgresIsKilled() throws Exception {
        int port = availablePort();
        String url = "jdbc:postgresql://127.0.0.1:" + port + "/postgres";
        Path output = processLogs.resolve("crashed-application.log");
        Process application = startApplicationProcess(port, output);
        try {
            awaitApplicationStarted(application, output);
            insertRow(url);
            killPostgresProcess();
            assertThat(application.waitFor(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            stopApplicationProcess(application);
        }
        try (var restarted = startApplication(port)) {
            verifyRow(url);
        }
    }

    @Test
    @Timeout(90)
    void replacesExistingApplicationAndPreservesData() throws Exception {
        int port = availablePort();
        String url = "jdbc:postgresql://127.0.0.1:" + port + "/postgres";
        Path previousOutput = processLogs.resolve("previous-application.log");
        Process previous = startApplicationProcess(port, previousOutput);
        try {
            awaitApplicationStarted(previous, previousOutput);
            insertRow(url);
            Path replacementOutput = processLogs.resolve("replacement-application.log");
            Process replacement = startApplicationProcess(port, replacementOutput);
            try {
                assertThat(previous.waitFor(15, TimeUnit.SECONDS)).isTrue();
                awaitApplicationStarted(replacement, replacementOutput);
                verifyRow(url);
            } finally {
                stopApplicationProcess(replacement);
            }
        } finally {
            stopApplicationProcess(previous);
        }
    }

    private int availablePort() throws Exception {
        try (var socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    private ConfigurableApplicationContext startApplication(int port) {
        return new SpringApplicationBuilder(EmbeddedPostgresApplication.class).run(
                "--embedded.postgres.port=" + port,
                "--embedded.postgres.data-directory=" + dataDirectory,
                "--spring.main.keep-alive=false",
                "--logging.level.root=WARN");
    }

    private void insertRow(String url) throws Exception {
        try (var connection = DriverManager.getConnection(url, "postgres", "postgres");
             var statement = connection.createStatement()) {
            try (var settings = statement.executeQuery("select current_setting('fsync'), "
                    + "current_setting('synchronous_commit'), current_setting('full_page_writes')")) {
                assertThat(settings.next()).isTrue();
                assertThat(settings.getString(1)).isEqualTo("on");
                assertThat(settings.getString(2)).isEqualTo("on");
                assertThat(settings.getString(3)).isEqualTo("on");
            }
            statement.execute("create table smoke_test (value text not null)");
            statement.executeUpdate("insert into smoke_test (value) values ('persisted')");
        }
    }

    private void verifyRow(String url) throws Exception {
        try (var connection = DriverManager.getConnection(url, "postgres", "postgres");
             var statement = connection.createStatement();
             var rows = statement.executeQuery("select value from smoke_test")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo("persisted");
        }
    }

    private Process startApplicationProcess(int port, Path output) throws Exception {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        String java = Path.of(System.getProperty("java.home"), "bin", executable).toString();
        return new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                EmbeddedPostgresApplication.class.getName(),
                "--embedded.postgres.port=" + port,
                "--embedded.postgres.data-directory=" + dataDirectory)
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
    }

    private void awaitApplicationStarted(Process process, Path output) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            String log = Files.readString(output);
            assertThat(process.isAlive()).as(log).isTrue();
            if (log.contains("Started EmbeddedPostgresApplication")) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Application did not start: " + Files.readString(output));
    }

    private void killPostgresProcess() throws Exception {
        String pid = Files.readString(dataDirectory.resolve("postmaster.pid")).lines().findFirst().orElseThrow();
        ProcessHandle process = ProcessHandle.of(Long.parseLong(pid)).orElseThrow();
        assertThat(process.destroyForcibly()).isTrue();
        process.onExit().get(10, TimeUnit.SECONDS);
    }

    private void stopApplicationProcess(Process process) throws Exception {
        if (process.isAlive()) {
            process.destroy();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }
}
