package embeded.kafka;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("embedded.kafka")
record KafkaProperties(String bindHost, String advertisedHost, int port, int controllerPort,
                       int partitions, Path dataDirectory) {
}
