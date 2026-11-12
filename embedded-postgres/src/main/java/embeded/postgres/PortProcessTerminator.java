package embeded.postgres;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class PortProcessTerminator {

    private static final Logger log = LoggerFactory.getLogger(PortProcessTerminator.class);
    private static final Duration RELEASE_TIMEOUT = Duration.ofSeconds(5);

    void release(String host, int port, Path dataDirectory) {
        tryReleaseDataDirectory(dataDirectory);
        tryReleasePort(host, port);
    }

    private void tryReleaseDataDirectory(Path dataDirectory) {
        try {
            releaseDataDirectory(dataDirectory);
        } catch (Exception exception) {
            ignoreTerminationFailure("data directory " + dataDirectory, exception);
        }
    }

    private void releaseDataDirectory(Path dataDirectory) throws IOException, InterruptedException {
        Path lockFile = dataDirectory.toAbsolutePath().resolve("epg-lock");
        if (isWindows() || !Files.exists(lockFile)) {
            return;
        }
        List<ProcessHandle> owners = processesFromOutput(
                execute(List.of("lsof", "-nP", "-t", "--", lockFile.toString())));
        for (ProcessHandle process : owners) {
            if (process.pid() == ProcessHandle.current().pid() || !isEmbeddedPostgresApplication(process)) {
                throw new IOException("PostgreSQL data directory " + dataDirectory
                        + " is in use by process " + process.pid());
            }
        }
        for (ProcessHandle process : owners) {
            log.warn("Stopping EmbeddedPostgresApplication {} holding {}", process.pid(), lockFile);
            process.destroy();
        }
        long deadline = System.nanoTime() + RELEASE_TIMEOUT.toNanos();
        while (owners.stream().anyMatch(ProcessHandle::isAlive) && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }
        if (owners.stream().anyMatch(ProcessHandle::isAlive)) {
            throw new IOException("Previous EmbeddedPostgresApplication did not release " + lockFile);
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    private String execute(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            if (!process.waitFor(RELEASE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IOException("Command timed out while releasing PostgreSQL port: " + command.getFirst());
            }
            if (process.exitValue() > 1) {
                throw new IOException("Cannot release PostgreSQL port: " + command.getFirst()
                        + " exited with code " + process.exitValue());
            }
            return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private List<ProcessHandle> processesFromOutput(String output) {
        return output.lines().map(String::trim).filter(line -> line.matches("[0-9]+"))
                .mapToLong(Long::parseLong).distinct().mapToObj(ProcessHandle::of)
                .flatMap(java.util.Optional::stream).toList();
    }

    private boolean isEmbeddedPostgresApplication(ProcessHandle process) {
        String[] arguments = process.info().arguments().orElseGet(() -> new String[0]);
        for (int index = 0; index < arguments.length; index++) {
            if (arguments[index].endsWith(".EmbeddedPostgresApplication")) {
                return true;
            }
            if (index > 0 && arguments[index - 1].equals("-jar")
                    && Path.of(arguments[index]).getFileName().toString().matches("embedded-postgres(?:-.*)?[.]jar")) {
                return true;
            }
        }
        return false;
    }

    private void ignoreTerminationFailure(String resource, Exception exception) {
        if (exception instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        log.warn("Could not release PostgreSQL {}; ignoring termination failure: {}", resource, exception.toString());
    }

    private void tryReleasePort(String host, int port) {
        try {
            releasePort(host, port);
        } catch (Exception exception) {
            ignoreTerminationFailure("address " + host + ":" + port, exception);
        }
    }

    private void releasePort(String host, int port) throws IOException, InterruptedException {
        if (isPortAvailable(host, port)) {
            return;
        }
        List<ProcessHandle> owners = findPortOwners(port);
        stopProcesses(owners, port);
        if (!awaitPortReleased(owners, host, port)) {
            forceStopProcesses(owners, port);
            verifyPortReleased(owners, host, port);
        }
    }

    private boolean isPortAvailable(String host, int port) throws IOException {
        try (var socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(host, port));
            return true;
        } catch (BindException exception) {
            return false;
        }
    }

    private List<ProcessHandle> findPortOwners(int port) throws IOException, InterruptedException {
        List<String> command = portOwnersCommand(port);
        String output = execute(command);
        List<ProcessHandle> owners = processesFromOutput(output);
        if (owners.stream().anyMatch(process -> process.pid() == ProcessHandle.current().pid())) {
            throw new IllegalStateException("Port " + port + " is occupied by the current JVM; cannot terminate itself");
        }
        return owners;
    }

    private List<String> portOwnersCommand(int port) {
        if (isWindows()) {
            return List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                    "(Get-NetTCPConnection -LocalPort " + port
                            + " -State Listen -ErrorAction SilentlyContinue).OwningProcess | Sort-Object -Unique");
        }
        return List.of("lsof", "-nP", "-t", "-iTCP:" + port, "-sTCP:LISTEN");
    }

    private void stopProcesses(List<ProcessHandle> owners, int port) throws IOException, InterruptedException {
        for (ProcessHandle process : owners) {
            log.warn("Stopping process {} occupying PostgreSQL port {}", process.pid(), port);
            if (!isWindows() && isPostgres(process)) {
                execute(List.of("kill", "-INT", Long.toString(process.pid())));
            } else {
                process.destroy();
            }
        }
    }

    private boolean isPostgres(ProcessHandle process) {
        return process.info().command().map(command -> Path.of(command).getFileName().toString())
                .filter("postgres"::equals).isPresent();
    }

    private boolean awaitPortReleased(List<ProcessHandle> owners, String host, int port)
            throws IOException, InterruptedException {
        long deadline = System.nanoTime() + RELEASE_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (owners.stream().noneMatch(ProcessHandle::isAlive) && isPortAvailable(host, port)) {
                return true;
            }
            Thread.sleep(100);
        }
        return false;
    }

    private void forceStopProcesses(List<ProcessHandle> owners, int port) {
        for (ProcessHandle process : owners) {
            if (process.isAlive()) {
                log.warn("Force stopping process {} occupying PostgreSQL port {}", process.pid(), port);
                process.destroyForcibly();
            }
        }
    }

    private void verifyPortReleased(List<ProcessHandle> owners, String host, int port)
            throws IOException, InterruptedException {
        if (!awaitPortReleased(owners, host, port)) {
            throw new IOException("Could not release PostgreSQL address " + host + ":" + port);
        }
    }
}
