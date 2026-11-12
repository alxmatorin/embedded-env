package embeded.postgres;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class PortProcessTerminatorTest {

    @TempDir
    Path dataDirectory;

    @Test
    @Timeout(20)
    void terminatesTheProcessOccupyingThePort() throws Exception {
        int port = availablePort();
        Process process = startSocketServer(port);
        try (var output = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            assertThat(output.readLine()).isEqualTo("READY");

            new PortProcessTerminator().release("127.0.0.1", port, dataDirectory);

            assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
            try (var socket = new ServerSocket(port, 0, InetAddress.getLoopbackAddress())) {
                assertThat(socket.isBound()).isTrue();
            }
        } finally {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void ignoresFailureWhenTheCurrentJvmOwnsThePort(CapturedOutput output) throws Exception {
        try (var socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            new PortProcessTerminator().release("127.0.0.1", socket.getLocalPort(), dataDirectory);

            assertThat(socket.isBound()).isTrue();
            assertThat(ProcessHandle.current().isAlive()).isTrue();
            assertThat(output).contains("ignoring termination failure", "current JVM");
        }
    }

    @Test
    @Timeout(20)
    @DisabledOnOs(OS.WINDOWS)
    void releasesThePortWhenTheDataDirectoryCannotBeReleased(CapturedOutput output) throws Exception {
        int port = availablePort();
        Process process = startSocketServer(port);
        try (var channel = FileChannel.open(dataDirectory.resolve("epg-lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.lock();
             var processOutput = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            assertThat(processOutput.readLine()).isEqualTo("READY");

            new PortProcessTerminator().release("127.0.0.1", port, dataDirectory);

            assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
            assertThat(lock.isValid()).isTrue();
            assertThat(output).contains("ignoring termination failure", "data directory", "is in use by process");
        } finally {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
    }

    private int availablePort() throws Exception {
        try (var socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    private Process startSocketServer(int port) throws Exception {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        String java = Path.of(System.getProperty("java.home"), "bin", executable).toString();
        return new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                SocketServer.class.getName(), Integer.toString(port)).redirectErrorStream(true).start();
    }

    public static class SocketServer {

        public static void main(String[] args) throws Exception {
            listenOnPort(Integer.parseInt(args[0]));
        }

        private static void listenOnPort(int port) throws Exception {
            try (var socket = new ServerSocket(port, 0, InetAddress.getLoopbackAddress())) {
                System.out.println("READY");
                while (true) {
                    socket.accept().close();
                }
            }
        }
    }
}
