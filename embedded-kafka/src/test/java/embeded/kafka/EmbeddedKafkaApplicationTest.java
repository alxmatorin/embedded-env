package embeded.kafka;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class EmbeddedKafkaApplicationTest {

    @TempDir
    Path dataDirectory;

    @Test
    @Timeout(90)
    void servesClientsAndPreservesCommittedMessagesAfterRestart() throws Exception {
        int[] ports = availablePorts();
        String address = "127.0.0.1:" + ports[0];
        try (var application = startApplication(ports)) {
            publishCommittedMessage(address);
        }
        try (var application = startApplication(ports)) {
            consumeCommittedMessage(address);
        }
    }

    private int[] availablePorts() throws Exception {
        try (var broker = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
             var controller = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            return new int[]{broker.getLocalPort(), controller.getLocalPort()};
        }
    }

    private ConfigurableApplicationContext startApplication(int[] ports) {
        return new SpringApplicationBuilder(EmbeddedKafkaApplication.class).run(
                "--embedded.kafka.port=" + ports[0],
                "--embedded.kafka.controller-port=" + ports[1],
                "--embedded.kafka.advertised-host=127.0.0.1",
                "--embedded.kafka.data-directory=" + dataDirectory,
                "--logging.level.root=WARN");
    }

    private void publishCommittedMessage(String address) throws Exception {
        Map<String, Object> properties = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, address,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.TRANSACTIONAL_ID_CONFIG, "test-producer",
                ProducerConfig.MAX_BLOCK_MS_CONFIG, 15000,
                ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 15000,
                ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 10000);
        try (var producer = new KafkaProducer<String, String>(properties)) {
            producer.initTransactions();
            producer.beginTransaction();
            producer.send(new ProducerRecord<>("smoke-test", "key", "value")).get(15, TimeUnit.SECONDS);
            producer.commitTransaction();
        }
    }

    private void consumeCommittedMessage(String address) {
        Map<String, Object> properties = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, address,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        try (var consumer = new KafkaConsumer<String, String>(properties)) {
            consumer.subscribe(List.of("smoke-test"));
            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (System.nanoTime() < deadline) {
                var records = consumer.poll(Duration.ofMillis(500));
                if (!records.isEmpty()) {
                    assertThat(records).singleElement().satisfies(record -> {
                        assertThat(record.key()).isEqualTo("key");
                        assertThat(record.value()).isEqualTo("value");
                    });
                    consumer.commitSync(Duration.ofSeconds(10));
                    return;
                }
            }
            fail("No committed message received from the restarted broker at " + address);
        }
    }
}
