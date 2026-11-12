package embeded.ignite.connections;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("connection-test")
record IgniteTestProperties(Duration timeout, String cache) {
}
