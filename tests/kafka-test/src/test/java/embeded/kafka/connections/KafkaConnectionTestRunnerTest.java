package embeded.kafka.connections;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.errors.TopicExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class KafkaConnectionTestRunnerTest {

    private final Admin admin = mock(Admin.class);
    private final CreateTopicsResult topics = mock(CreateTopicsResult.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
    @SuppressWarnings("unchecked")
    private final ConsumerFactory<String, String> consumerFactory = mock(ConsumerFactory.class);
    @SuppressWarnings("unchecked")
    private final Consumer<String, String> consumer = mock(Consumer.class);
    private final TopicPartition partition = new TopicPartition("test-topic", 0);
    private final AtomicReference<String> sentKey = new AtomicReference<>();
    private final KafkaTestProperties properties = new KafkaTestProperties(Duration.ofSeconds(1), "test-topic");
    private final KafkaConnectionTestRunner runner = new KafkaConnectionTestRunner(
            admin, template, consumerFactory, properties);

    @BeforeEach
    void configureBroker() {
        when(admin.createTopics(anyCollection())).thenReturn(topics);
        when(topics.all()).thenReturn(KafkaFuture.completedFuture(null));
        when(consumerFactory.createConsumer(anyString(), eq("connection-test"))).thenReturn(consumer);
        when(consumer.partitionsFor("test-topic", properties.timeout())).thenReturn(
                List.of(new PartitionInfo("test-topic", 0, null, null, null)));
        when(consumer.endOffsets(List.of(partition), properties.timeout())).thenReturn(Map.of(partition, 42L));
        when(template.send(eq("test-topic"), anyString(), anyString())).thenAnswer(invocation -> {
            sentKey.set(invocation.getArgument(1));
            return CompletableFuture.completedFuture(null);
        });
        when(consumer.poll(any(Duration.class))).thenAnswer(invocation -> receivedMessages());
    }

    @Test
    void readsNewMessageAfterExistingMessagesAndClosesConsumer(CapturedOutput output) {
        runner.run(new DefaultApplicationArguments());

        assertThat(output).contains("kafka: OK");
        var order = inOrder(consumer, template);
        order.verify(consumer).seek(partition, 42L);
        order.verify(template).send("test-topic", sentKey.get(), "connection-test:" + sentKey.get());
        order.verify(consumer).poll(any(Duration.class));
        verify(consumer).close();
    }

    @Test
    void testsExistingTopic(CapturedOutput output) {
        KafkaFuture<Void> failed = KafkaFuture.completedFuture(null).thenApply(ignored -> {
            throw new TopicExistsException("already exists");
        });
        when(topics.all()).thenReturn(failed);

        runner.run(new DefaultApplicationArguments());


        assertThat(output).contains("kafka: OK");
        verify(consumer).close();
    }

    @Test
    void reportsPublishFailureAndClosesConsumer(CapturedOutput output) {
        when(template.send(eq("test-topic"), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException("broker unavailable")));

        runner.run(new DefaultApplicationArguments());
        assertThat(output).contains("kafka: FAILED");
        assertThat(output).contains("broker unavailable");
        verify(consumer).close();
    }

    private ConsumerRecords<String, String> receivedMessages() {
        return new ConsumerRecords<>(Map.of(partition, List.of(
                new ConsumerRecord<>("test-topic", 0, 42L, "another-key", "another-value"),
                new ConsumerRecord<>("test-topic", 0, 43L, sentKey.get(), "connection-test:" + sentKey.get()))),
                Map.of());
    }
}
