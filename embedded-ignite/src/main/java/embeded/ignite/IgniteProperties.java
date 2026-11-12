package embeded.ignite;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("embedded.ignite")
record IgniteProperties(String host, int port, int discoveryPort, int communicationPort,
                        Path workDirectory, boolean persistenceEnabled) {
}
