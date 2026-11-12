package embeded.kafka;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import kafka.server.KafkaConfig;
import kafka.server.KafkaRaftServer;
import kafka.server.Server;
import org.apache.kafka.common.Uuid;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.metadata.storage.Formatter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KafkaProperties.class)
class KafkaConfiguration {

    @Bean(initMethod = "startup", destroyMethod = "shutdown")
    Server kafkaServer(KafkaProperties properties) throws Exception {
        KafkaConfig configuration = KafkaConfig.fromProps(brokerProperties(properties), false);
        initializeStorage(properties.dataDirectory());
        return new KafkaRaftServer(configuration, Time.SYSTEM);
    }

    private Properties brokerProperties(KafkaProperties properties) {
        Properties broker = new Properties();
        broker.setProperty("process.roles", "broker,controller");
        broker.setProperty("node.id", "1");
        broker.setProperty("listeners", "PLAINTEXT://%s:%d,CONTROLLER://127.0.0.1:%d"
                .formatted(properties.bindHost(), properties.port(), properties.controllerPort()));
        broker.setProperty("advertised.listeners", "PLAINTEXT://%s:%d"
                .formatted(properties.advertisedHost(), properties.port()));
        broker.setProperty("controller.quorum.voters", "1@127.0.0.1:" + properties.controllerPort());
        broker.setProperty("controller.listener.names", "CONTROLLER");
        broker.setProperty("inter.broker.listener.name", "PLAINTEXT");
        broker.setProperty("listener.security.protocol.map", "PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT");
        broker.setProperty("log.dirs", properties.dataDirectory().toAbsolutePath().toString());
        broker.setProperty("num.partitions", Integer.toString(properties.partitions()));
        broker.setProperty("default.replication.factor", "1");
        broker.setProperty("offsets.topic.replication.factor", "1");
        broker.setProperty("offsets.topic.num.partitions", "1");
        broker.setProperty("transaction.state.log.replication.factor", "1");
        broker.setProperty("transaction.state.log.min.isr", "1");
        broker.setProperty("transaction.state.log.num.partitions", "1");
        broker.setProperty("group.initial.rebalance.delay.ms", "0");
        broker.setProperty("auto.create.topics.enable", "true");
        return broker;
    }

    private void initializeStorage(Path directory) throws Exception {
        Files.createDirectories(directory);
        if (!Files.exists(directory.resolve("meta.properties"))) {
            new Formatter()
                    .setNodeId(1)
                    .setClusterId(Uuid.randomUuid().toString())
                    .setControllerListenerName("CONTROLLER")
                    .setDirectories(List.of(directory.toAbsolutePath().toString()))
                    .run();
        }
    }
}
