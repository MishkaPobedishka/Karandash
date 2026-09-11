package ru.karandash.agent.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Запускает CLI-агента отдельным процессом с минимальными правами:
 * пустой рабочий каталог, окружение с нуля по белому списку, пользовательский ввод только через stdin,
 * таймаут с убийством всего дерева процессов и лимит объёма вывода.
 */
public class CliProcessRunner {

    private static final Logger log = LoggerFactory.getLogger(CliProcessRunner.class);
    private static final int STDERR_EXCERPT_CHARS = 2048;
    private static final Duration STREAM_JOIN_TIMEOUT = Duration.ofSeconds(5);
    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    /** Без них процессы на Windows не стартуют; секретов в них нет. */
    private static final List<String> WINDOWS_SYSTEM_VARIABLES =
            List.of("SystemRoot", "windir", "SystemDrive", "PATHEXT", "ComSpec", "OS",
                    "PROCESSOR_ARCHITECTURE", "NUMBER_OF_PROCESSORS");

    private final Map<String, String> parentEnvironment;

    public CliProcessRunner(Map<String, String> parentEnvironment) {
        this.parentEnvironment = Map.copyOf(parentEnvironment);
    }

    public boolean hasAnyVariable(Set<String> names) {
        return names.stream().map(this::parentVariable).anyMatch(value -> value != null && !value.isBlank());
    }

    public ProcessOutcome run(
            CliInvocation invocation,
            CallDirectory directory,
            Consumer<String> lineListener,
            Duration timeout,
            int maxOutputBytes
    ) {
        ProcessBuilder builder = new ProcessBuilder(invocation.command()).directory(directory.work().toFile());
        Map<String, String> environment = builder.environment();
        environment.clear();
        environment.putAll(childEnvironment(invocation, directory));

        long startedAt = System.nanoTime();
        Process process;
        try {
            process = builder.start();
        } catch (IOException exception) {
            throw new CliCallException(CliCallException.Reason.START_FAILED, "Не удалось запустить CLI", exception);
        }

        AtomicReference<CliCallException> failure = new AtomicReference<>();
        StringBuilder stderrExcerpt = new StringBuilder();
        Thread stdinWriter = Thread.ofVirtual().name("cli-stdin").start(() -> writeStdin(process, invocation.stdin()));
        Thread stderrReader = Thread.ofVirtual().name("cli-stderr").start(() -> drainStderr(process, stderrExcerpt));
        Thread stdoutReader = Thread.ofVirtual().name("cli-stdout")
                .start(() -> readStdout(process, lineListener, maxOutputBytes, failure));
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                failure.compareAndSet(null, new CliCallException(CliCallException.Reason.TIMEOUT,
                        "CLI не уложился в " + timeout.toSeconds() + " с"));
                killTree(process);
            }
            join(stdoutReader);
            join(stderrReader);
            join(stdinWriter);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            killTree(process);
            throw new CliCallException(CliCallException.Reason.FAILED, "Вызов CLI прерван", exception);
        } finally {
            if (process.isAlive()) {
                killTree(process);
            }
        }

        if (failure.get() != null) {
            throw failure.get();
        }
        String excerpt;
        synchronized (stderrExcerpt) {
            excerpt = stderrExcerpt.toString();
        }
        return new ProcessOutcome(process.exitValue(), excerpt, Duration.ofNanos(System.nanoTime() - startedAt));
    }

    Map<String, String> childEnvironment(CliInvocation invocation, CallDirectory directory) {
        Map<String, String> environment = new LinkedHashMap<>();
        copyParentVariable(environment, "PATH");
        environment.put("LANG", "C.UTF-8");
        environment.put("LC_ALL", "C.UTF-8");
        environment.put("NO_COLOR", "1");
        environment.put("HOME", directory.home().toString());
        environment.put("TMPDIR", directory.tmp().toString());
        if (WINDOWS) {
            WINDOWS_SYSTEM_VARIABLES.forEach(name -> copyParentVariable(environment, name));
            environment.put("USERPROFILE", directory.home().toString());
            environment.put("APPDATA", directory.config().resolve("appdata").toString());
            environment.put("LOCALAPPDATA", directory.config().resolve("localappdata").toString());
            environment.put("TEMP", directory.tmp().toString());
            environment.put("TMP", directory.tmp().toString());
        }
        environment.putAll(invocation.environment());
        invocation.passthroughVariables().forEach(name -> copyParentVariable(environment, name));
        return environment;
    }

    static void killTree(Process process) {
        List<ProcessHandle> descendants = process.descendants().toList();
        descendants.forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        try {
            process.waitFor(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void copyParentVariable(Map<String, String> environment, String name) {
        String value = parentVariable(name);
        if (value != null && !value.isBlank()) {
            environment.put(name, value);
        }
    }

    private String parentVariable(String name) {
        String value = parentEnvironment.get(name);
        if (value != null || !WINDOWS) {
            return value;
        }
        return parentEnvironment.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private static void writeStdin(Process process, byte[] stdin) {
        try (OutputStream input = process.getOutputStream()) {
            input.write(stdin);
        } catch (IOException ignored) {
            // процесс завершился или убит раньше, чем дочитал stdin
        }
    }

    private static void drainStderr(Process process, StringBuilder excerpt) {
        try (Reader reader = new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8)) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                synchronized (excerpt) {
                    int room = STDERR_EXCERPT_CHARS - excerpt.length();
                    if (room > 0) {
                        excerpt.append(buffer, 0, Math.min(room, read));
                    }
                }
            }
        } catch (IOException ignored) {
            // поток закрыт после завершения процесса
        }
    }

    private static void readStdout(
            Process process,
            Consumer<String> lineListener,
            int maxOutputBytes,
            AtomicReference<CliCallException> failure
    ) {
        try (Reader reader = new InputStreamReader(
                new LimitedInputStream(process.getInputStream(), maxOutputBytes), StandardCharsets.UTF_8)) {
            char[] buffer = new char[8192];
            StringBuilder line = new StringBuilder();
            int read;
            while ((read = reader.read(buffer)) != -1) {
                for (int index = 0; index < read; index++) {
                    char current = buffer[index];
                    if (current == '\n') {
                        lineListener.accept(line.toString());
                        line.setLength(0);
                    } else if (current != '\r') {
                        line.append(current);
                    }
                }
            }
            if (!line.isEmpty()) {
                lineListener.accept(line.toString());
            }
        } catch (CliCallException exception) {
            failure.compareAndSet(null, exception);
            killTree(process);
        } catch (IOException ignored) {
            // поток закрыт после убийства процесса
        } catch (RuntimeException exception) {
            failure.compareAndSet(null, new CliCallException(CliCallException.Reason.FAILED,
                    "Не удалось разобрать вывод CLI", exception));
            killTree(process);
        }
    }

    private static void join(Thread thread) throws InterruptedException {
        if (!thread.join(STREAM_JOIN_TIMEOUT)) {
            log.warn("Поток {} не завершился после остановки CLI", thread.getName());
        }
    }

    /** Считает байты stdout и обрывает чтение, как только лимит превышен. */
    private static final class LimitedInputStream extends FilterInputStream {

        private final long limit;
        private long total;

        LimitedInputStream(InputStream input, long limit) {
            super(input);
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value != -1) {
                count(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) {
                count(read);
            }
            return read;
        }

        private void count(int read) {
            total += read;
            if (total > limit) {
                throw new CliCallException(CliCallException.Reason.OUTPUT_LIMIT, "CLI превысил лимит вывода");
            }
        }
    }
}
