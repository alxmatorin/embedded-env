package embeded.ignite.connections;

import java.time.Duration;

import org.apache.ignite.Ignition;
import org.apache.ignite.client.ClientCache;
import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.configuration.ClientConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class IgniteConnectionTestRunnerTest {

    private final IgniteClient client = mock(IgniteClient.class);
    @SuppressWarnings("unchecked")
    private final ClientCache<String, String> cache = mock(ClientCache.class);
    private final IgniteTestProperties properties = new IgniteTestProperties(Duration.ofSeconds(1), "test-cache");
    private final IgniteConnectionTestRunner runner = new IgniteConnectionTestRunner(
            new String[]{"127.0.0.1:10800"}, properties);

    @Test
    void writesAndReadsCacheEntryThenRemovesItAndClosesClient(CapturedOutput output) {
        try (var ignition = mockStatic(Ignition.class)) {
            ignition.when(() -> Ignition.startClient(any(ClientConfiguration.class))).thenReturn(client);
            when(client.<String, String>getOrCreateCache("test-cache")).thenReturn(cache);
            when(cache.get(anyString())).thenAnswer(invocation -> "connection-test:" + invocation.getArgument(0));

            runner.run(new DefaultApplicationArguments());

            assertThat(output).contains("ignite: OK");
            var key = ArgumentCaptor.forClass(String.class);
            verify(cache).get(key.capture());
            verify(cache).put(key.getValue(), "connection-test:" + key.getValue());
            verify(cache).remove(key.getValue());
            verify(client).close();
        }
    }

    @Test
    void reportsUnexpectedValueAndStillRemovesEntryAndClosesClient(CapturedOutput output) {
        try (var ignition = mockStatic(Ignition.class)) {
            ignition.when(() -> Ignition.startClient(any(ClientConfiguration.class))).thenReturn(client);
            when(client.<String, String>getOrCreateCache("test-cache")).thenReturn(cache);
            when(cache.get(anyString())).thenReturn("unexpected");

            runner.run(new DefaultApplicationArguments());

            assertThat(output).contains("ignite: FAILED");
            assertThat(output).contains("Cache returned an unexpected value");
            verify(cache).remove(anyString());
            verify(client).close();
        }
    }

    @Test
    void reportsConnectionFailure(CapturedOutput output) {
        try (var ignition = mockStatic(Ignition.class)) {
            ignition.when(() -> Ignition.startClient(any(ClientConfiguration.class)))
                    .thenThrow(new IllegalStateException("cluster unavailable"));

            runner.run(new DefaultApplicationArguments());

            assertThat(output).contains("ignite: FAILED");
            assertThat(output).contains("cluster unavailable");
        }
    }
}
