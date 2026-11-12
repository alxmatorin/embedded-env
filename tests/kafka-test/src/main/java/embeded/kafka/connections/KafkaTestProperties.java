package embeded.kafka.connections;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("connection-test")
record KafkaTestProperties(Duration timeout, String topic) {
}
