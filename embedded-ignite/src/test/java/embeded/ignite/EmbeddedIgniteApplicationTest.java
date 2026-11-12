package embeded.ignite;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Path;

import org.apache.ignite.Ignition;
import org.apache.ignite.client.ClientCache;
import org.apache.ignite.configuration.ClientConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddedIgniteApplicationTest {

    @TempDir
    Path workDirectory;

    @Test
    @Timeout(60)
    void sharesCacheBetweenIndependentThinClientsAndClearsItOnRestart() throws Exception {
        int[] ports = availablePorts();
        String address = "127.0.0.1:" + ports[0];
        try (var application = startApplication(ports, false)) {
            shareCacheBetweenClients(address);
        }
        try (var application = startApplication(ports, false)) {
            verifyCacheWasCleared(address);
        }
    }

    @Test
    @Timeout(60)
    void sharesCacheBetweenIndependentThinClientsAndPreservesItOnRestartWithPersistence() throws Exception {
        int[] ports = availablePorts();
        String address = "127.0.0.1:" + ports[0];
        try (var application = startApplication(ports, true)) {
            shareCacheBetweenClients(address);
        }
        try (var application = startApplication(ports, true)) {
            verifyCacheSurvived(address);
        }
    }

    private int[] availablePorts() throws Exception {
        try (var client = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
             var discovery = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
             var communication = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            return new int[]{client.getLocalPort(), discovery.getLocalPort(), communication.getLocalPort()};
        }
    }

    private ConfigurableApplicationContext startApplication(int[] ports, boolean persistenceEnabled) {
        return new SpringApplicationBuilder(EmbeddedIgniteApplication.class).run(
                "--embedded.ignite.port=" + ports[0],
                "--embedded.ignite.discovery-port=" + ports[1],
                "--embedded.ignite.communication-port=" + ports[2],
                "--embedded.ignite.persistence-enabled=" + persistenceEnabled,
                "--embedded.ignite.work-directory=" + workDirectory,
                "--logging.level.root=WARN");
    }

    private void shareCacheBetweenClients(String address) {
        var configuration = clientConfiguration(address);
        try (var writer = Ignition.startClient(configuration);
             var reader = Ignition.startClient(configuration)) {
            ClientCache<String, String> cache = writer.getOrCreateCache("smoke-test");
            cache.put("key", "value");
            assertThat(reader.<String, String>cache("smoke-test").get("key")).isEqualTo("value");
        }
    }

    private ClientConfiguration clientConfiguration(String address) {
        return new ClientConfiguration().setAddresses(address)
                .setHandshakeTimeout(10000).setRequestTimeout(10000);
    }

    private void verifyCacheWasCleared(String address) {
        try (var client = Ignition.startClient(clientConfiguration(address))) {
            assertThat(client.cacheNames()).doesNotContain("smoke-test");
        }
    }

    private void verifyCacheSurvived(String address) {
        try (var client = Ignition.startClient(clientConfiguration(address))) {
            assertThat(client.<String, String>cache("smoke-test").get("key")).isEqualTo("value");
        }
    }
}
