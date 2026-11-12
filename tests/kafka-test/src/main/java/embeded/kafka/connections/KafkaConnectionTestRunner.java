package embeded.kafka.connections;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.TopicExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
class KafkaConnectionTestRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KafkaConnectionTestRunner.class);

    private final Admin admin;
    private final KafkaTemplate<String, String> template;
    private final ConsumerFactory<String, String> consumerFactory;
    private final KafkaTestProperties properties;

    KafkaConnectionTestRunner(@Lazy Admin admin, KafkaTemplate<String, String> template,
                              ConsumerFactory<String, String> consumerFactory, KafkaTestProperties properties) {
        this.admin = admin;
        this.template = template;
        this.consumerFactory = consumerFactory;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        testConnection();
    }

    private void testConnection() {
        try {
            log.info("kafka: OK — {}", exchangeMessage());
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("kafka: FAILED — {}", exception.toString());
        }
    }

    private String exchangeMessage() throws Exception {
        ensureTopicExists();
        String key = UUID.randomUUID().toString();
        try (var consumer = consumerFactory.createConsumer("connection-test-" + key, "connection-test")) {
            startReadingAtCurrentEnd(consumer);
            publishMessage(key);
            awaitMessage(consumer, key);
        }
        return "Message sent and received; topic=" + properties.topic();
    }

    private void ensureTopicExists() throws Exception {
        try {
            admin.createTopics(List.of(new NewTopic(properties.topic(), 1, (short) 1)))
                    .all().get(properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException exception) {
            if (!(exception.getCause() instanceof TopicExistsException)) {
                throw exception;
            }
        }
    }

    private void startReadingAtCurrentEnd(Consumer<String, String> consumer) {
        List<TopicPartition> partitions = consumer.partitionsFor(properties.topic(), properties.timeout())
                .stream().map(partition -> new TopicPartition(partition.topic(), partition.partition())).toList();
        consumer.assign(partitions);
        consumer.endOffsets(partitions, properties.timeout()).forEach(consumer::seek);
    }

    private void publishMessage(String key) throws Exception {
        template.send(properties.topic(), key, "connection-test:" + key)
                .get(properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
    }

    private void awaitMessage(Consumer<String, String> consumer, String key) {
        long deadline = System.nanoTime() + properties.timeout().toNanos();
        while (System.nanoTime() < deadline) {
            for (var record : consumer.poll(Duration.ofMillis(100))) {
                if (key.equals(record.key()) && ("connection-test:" + key).equals(record.value())) {
                    return;
                }
            }
        }
        throw new IllegalStateException("Message was not received within " + properties.timeout());
    }
}
