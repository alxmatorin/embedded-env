package embeded.ignite.connections;

import java.util.UUID;

import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.ClientConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
class IgniteConnectionTestRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IgniteConnectionTestRunner.class);

    private final String[] addresses;
    private final IgniteTestProperties properties;

    IgniteConnectionTestRunner(@Value("${app.ignite.addresses}") String[] addresses, IgniteTestProperties properties) {
        this.addresses = addresses;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        testConnection();
    }

    private void testConnection() {
        try {
            log.info("ignite: OK — {}", exchangeCacheEntry());
        } catch (Exception exception) {
            log.error("ignite: FAILED — {}", exception.toString());
        }
    }

    private String exchangeCacheEntry() {
        String key = UUID.randomUUID().toString();
        try (var client = Ignition.startClient(clientConfiguration())) {
            var cache = client.<String, String>getOrCreateCache(properties.cache());
            try {
                cache.put(key, "connection-test:" + key);
                verifyValue(key, cache.get(key));
            } finally {
                cache.remove(key);
            }
        }
        return "Cache entry written and read; cache=" + properties.cache();
    }

    private ClientConfiguration clientConfiguration() {
        int timeoutMillis = Math.toIntExact(properties.timeout().toMillis());
        return new ClientConfiguration()
                .setAddresses(addresses)
                .setHandshakeTimeout(timeoutMillis)
                .setRequestTimeout(timeoutMillis);
    }

    private void verifyValue(String key, String value) {
        if (!("connection-test:" + key).equals(value)) {
            throw new IllegalStateException("Cache returned an unexpected value");
        }
    }
}
