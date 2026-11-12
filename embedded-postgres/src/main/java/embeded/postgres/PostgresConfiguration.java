package embeded.postgres;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PostgresProperties.class)
class PostgresConfiguration {

    private final PortProcessTerminator portProcessTerminator = new PortProcessTerminator();

    @Bean(destroyMethod = "close")
    EmbeddedPostgres postgres(PostgresProperties properties, ConfigurableApplicationContext application)
            throws IOException {
        portProcessTerminator.release(properties.host(), properties.port(), properties.dataDirectory());
        EmbeddedPostgres.Builder builder = configureServer(properties);
        EmbeddedPostgres postgres = builder.start();
        closeApplicationWhenServerStops(properties, application);
        return postgres;
    }

    private EmbeddedPostgres.Builder configureServer(PostgresProperties properties) {
        return EmbeddedPostgres.builder()
                .setPort(properties.port())
                .setDataDirectory(properties.dataDirectory().toAbsolutePath())
                .setCleanDataDirectory(false)
                .setLocaleConfig("locale", "C")
                .setServerConfig("listen_addresses", properties.host())
                .setServerConfig("fsync", "on")
                .setServerConfig("synchronous_commit", "on")
                .setServerConfig("full_page_writes", "on");
    }

    private void closeApplicationWhenServerStops(PostgresProperties properties, ConfigurableApplicationContext application)
            throws IOException {
        Path pidFile = properties.dataDirectory().resolve("postmaster.pid");
        long pid = Long.parseLong(Files.readString(pidFile).lines().findFirst().orElseThrow());
        ProcessHandle process = ProcessHandle.of(pid)
                .orElseThrow(() -> new IOException("PostgreSQL process " + pid + " has stopped during startup"));
        process.onExit().thenRun(application::close);
    }
}
