package embeded.postgres;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("embedded.postgres")
record PostgresProperties(String host, int port, Path dataDirectory) {
}
