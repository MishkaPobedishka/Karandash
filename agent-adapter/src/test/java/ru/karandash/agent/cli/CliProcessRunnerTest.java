package ru.karandash.agent.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.karandash.agent.testing.FakeCliSupport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CliProcessRunnerTest {

    @TempDir
    Path temp;

    private FakeCliSupport fake;
    private CliProcessRunner runner;

    @BeforeEach
    void setUp() throws Exception {
        fake = new FakeCliSupport(temp);
        runner = new CliProcessRunner(FakeCliSupport.parentEnvironment(Map.of(
                "ANTHROPIC_API_KEY", "test-key",
                "AGENT_SERVICE_TOKEN", "agent-token-must-not-leak",
                "POSTGRES_PASSWORD", "db-password-must-not-leak",
                "UNRELATED", "value")));
    }

    @Test
    void childGetsOnlyAllowlistedEnvironmentAndEmptyWorkingDirectory() throws Exception {
        fake.mode("echo");
        byte[] stdin = new byte[2 * 1024 * 1024];
        new Random(42).nextBytes(stdin);

        try (CallDirectory directory = CallDirectory.create(temp.resolve("calls"))) {
            ProcessOutcome outcome = runner.run(
                    new CliInvocation(fake.command(), stdin, Map.of("CLAUDE_CONFIG_DIR", directory.config().toString()),
                            Set.of("ANTHROPIC_API_KEY")),
                    directory, line -> {
                    }, Duration.ofSeconds(30), 1024);

            assertThat(outcome.exitCode()).isZero();
            Map<String, String> environment = fake.recordedEnvironment();
            assertThat(environment)
                    .containsEntry("ANTHROPIC_API_KEY", "test-key")
                    .containsEntry("CLAUDE_CONFIG_DIR", directory.config().toString())
                    .containsEntry("HOME", directory.home().toString())
                    .doesNotContainKeys("AGENT_SERVICE_TOKEN", "POSTGRES_PASSWORD", "UNRELATED");
            assertThat(environment.values()).noneMatch(value -> value.contains("must-not-leak"));
            assertThat(fake.recordedWorkingDirectory()).isEqualTo(directory.work().toRealPath());
            assertThat(fake.recordedWorkingDirectoryFiles()).isEmpty();
            assertThat(fake.recordedStdin()).isEqualTo(stdin);
        }
    }

    @Test
    void timeoutKillsWholeProcessTree() throws Exception {
        fake.mode("hang-with-child");

        try (CallDirectory directory = CallDirectory.create(temp.resolve("calls"))) {
            assertThatThrownBy(() -> runner.run(
                    new CliInvocation(fake.command(), new byte[0], Map.of(), Set.of()),
                    directory, line -> {
                    }, Duration.ofSeconds(4), 1024))
                    .isInstanceOf(CliCallException.class)
                    .extracting(exception -> ((CliCallException) exception).reason())
                    .isEqualTo(CliCallException.Reason.TIMEOUT);
        }

        long childPid = Long.parseLong(Files.readString(fake.recordDirectory().resolve("child.pid")).trim());
        assertThat(waitUntilDead(childPid)).as("дочерний процесс CLI убит вместе с родителем").isTrue();
    }

    @Test
    void outputLimitStopsProcess() throws Exception {
        fake.mode("flood");
        long startedAt = System.nanoTime();

        try (CallDirectory directory = CallDirectory.create(temp.resolve("calls"))) {
            assertThatThrownBy(() -> runner.run(
                    new CliInvocation(fake.command(), new byte[0], Map.of(), Set.of()),
                    directory, line -> {
                    }, Duration.ofSeconds(60), 256 * 1024))
                    .isInstanceOf(CliCallException.class)
                    .extracting(exception -> ((CliCallException) exception).reason())
                    .isEqualTo(CliCallException.Reason.OUTPUT_LIMIT);
        }
        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(30));
    }

    @Test
    void listenerRejectionKillsProcessImmediately() throws Exception {
        fake.mode("claude-tool-use");
        long startedAt = System.nanoTime();

        try (CallDirectory directory = CallDirectory.create(temp.resolve("calls"))) {
            assertThatThrownBy(() -> runner.run(
                    new CliInvocation(fake.command(), new byte[0], Map.of(), Set.of()),
                    directory, line -> {
                        if (line.contains("tool_use")) {
                            throw new CliCallException(CliCallException.Reason.UNSAFE_BEHAVIOR, "tool_use");
                        }
                    }, Duration.ofSeconds(60), 1024 * 1024))
                    .isInstanceOf(CliCallException.class)
                    .extracting(exception -> ((CliCallException) exception).reason())
                    .isEqualTo(CliCallException.Reason.UNSAFE_BEHAVIOR);
        }
        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(20));
    }

    @Test
    void missingExecutableIsReportedAsStartFailure() throws Exception {
        try (CallDirectory directory = CallDirectory.create(temp.resolve("calls"))) {
            assertThatThrownBy(() -> runner.run(
                    new CliInvocation(java.util.List.of(temp.resolve("no-such-cli").toString()), new byte[0],
                            Map.of(), Set.of()),
                    directory, line -> {
                    }, Duration.ofSeconds(5), 1024))
                    .isInstanceOf(CliCallException.class)
                    .extracting(exception -> ((CliCallException) exception).reason())
                    .isEqualTo(CliCallException.Reason.START_FAILED);
        }
    }

    @Test
    void callDirectoryIsDeletedOnClose() throws Exception {
        Path root;
        try (CallDirectory directory = CallDirectory.create(temp.resolve("calls"))) {
            root = directory.root();
            Files.write(directory.root().resolve("input.jpg"), new byte[]{1, 2, 3});
            Files.writeString(directory.config().resolve(".claude.json"), "{}");
        }
        assertThat(root).doesNotExist();
    }

    private static boolean waitUntilDead(long pid) throws InterruptedException {
        for (int attempt = 0; attempt < 50; attempt++) {
            if (ProcessHandle.of(pid).map(handle -> !handle.isAlive()).orElse(true)) {
                return true;
            }
            Thread.sleep(100);
        }
        return false;
    }
}
