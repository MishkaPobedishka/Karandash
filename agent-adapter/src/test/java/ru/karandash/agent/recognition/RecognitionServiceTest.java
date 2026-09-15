package ru.karandash.agent.recognition;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;
import ru.karandash.agent.AgentProperties;
import ru.karandash.agent.cli.AgentCli;
import ru.karandash.agent.cli.CallDirectory;
import ru.karandash.agent.cli.CliAuthState;
import ru.karandash.agent.cli.CliCallException;
import ru.karandash.agent.cli.CliInvocation;
import ru.karandash.agent.cli.CliOutputHandler;
import ru.karandash.agent.cli.CliProcessRunner;
import ru.karandash.agent.cli.RecognitionTask;
import ru.karandash.agent.testing.FakeCli;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecognitionServiceTest {

    @TempDir
    Path temp;

    @Test
    void limitsParallelCallsAndCleansCallDirectory() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Path> callRoot = new AtomicReference<>();
        AgentCli blockingCli = new StubCli() {
            @Override
            public CliInvocation prepare(RecognitionTask task, CallDirectory directory) throws IOException {
                callRoot.set(directory.root());
                entered.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                throw new IOException("stop");
            }
        };
        RecognitionService service = service(blockingCli, 1, Duration.ofMillis(200));

        CompletableFuture<Void> first = CompletableFuture.runAsync(() -> service.recognizeText("суп"));
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> service.recognizeText("борщ")).isInstanceOf(AgentBusyException.class);

        release.countDown();
        assertThatThrownBy(first::join).hasCauseInstanceOf(CliCallException.class);
        assertThat(callRoot.get()).doesNotExist();
    }

    @Test
    void acceptsJsonWrappedInProseOrCodeFence() {
        RecognitionService service = service(new StubCli(), 1, Duration.ofSeconds(1));

        assertThat(service.parseResult("Вот оценка:\n" + FakeCli.RECOGNITION_JSON + "\nГотово.").items()).hasSize(1);
        assertThat(service.parseResult("```json\n" + FakeCli.RECOGNITION_JSON + "\n```").items()).hasSize(1);
        assertThatThrownBy(() -> service.parseResult("не знаю"))
                .isInstanceOf(CliCallException.class)
                .extracting(exception -> ((CliCallException) exception).reason())
                .isEqualTo(CliCallException.Reason.BAD_OUTPUT);
    }

    private RecognitionService service(AgentCli cli, int maxConcurrentCalls, Duration queueTimeout) {
        AgentProperties properties = new AgentProperties("token", "claude", Duration.ofSeconds(5), maxConcurrentCalls,
                queueTimeout, 1024 * 1024, 2000, DataSize.ofMegabytes(10), temp.resolve("calls"),
                Duration.ZERO, new AgentProperties.Claude(List.of("claude"), "sonnet", null, BigDecimal.ONE),
                new AgentProperties.Codex(List.of("codex"), null, null, null));
        return new RecognitionService(cli, new CliProcessRunner(Map.of()), new CliAuthState(), properties,
                new ObjectMapper());
    }

    private static class StubCli implements AgentCli {

        @Override
        public String provider() {
            return "stub";
        }

        @Override
        public Set<String> credentialVariables() {
            return Set.of();
        }

        @Override
        public CliInvocation prepare(RecognitionTask task, CallDirectory directory) throws IOException {
            throw new IOException("not used");
        }

        @Override
        public CliOutputHandler newOutputHandler() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CliInvocation healthCheck(CallDirectory directory) {
            throw new UnsupportedOperationException();
        }
    }
}
