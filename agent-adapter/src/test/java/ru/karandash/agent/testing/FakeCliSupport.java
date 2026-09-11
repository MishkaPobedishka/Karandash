package ru.karandash.agent.testing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Команда запуска {@link FakeCli} и чтение того, что он записал.
 */
public final class FakeCliSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path recordDirectory;
    private final Path controlFile;

    public FakeCliSupport(Path baseDirectory) throws IOException {
        this.recordDirectory = Files.createDirectories(baseDirectory.resolve("record"));
        this.controlFile = baseDirectory.resolve("fake-mode.txt");
    }

    public List<String> command() {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java").toString());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add("-Dfake.control=" + controlFile);
        command.add("-Dfake.record=" + recordDirectory);
        command.add(FakeCli.class.getName());
        return command;
    }

    public void mode(String mode) throws IOException {
        Files.writeString(controlFile, mode);
        try (var files = Files.list(recordDirectory)) {
            for (Path file : files.toList()) {
                if (Files.isRegularFile(file)) {
                    Files.delete(file);
                }
            }
        }
    }

    public Path recordDirectory() {
        return recordDirectory;
    }

    public List<String> recordedArguments() throws IOException {
        return MAPPER.readValue(recordDirectory.resolve("argv.json").toFile(), new TypeReference<>() {
        });
    }

    public Map<String, String> recordedEnvironment() throws IOException {
        return MAPPER.readValue(recordDirectory.resolve("env.json").toFile(), new TypeReference<>() {
        });
    }

    public byte[] recordedStdin() throws IOException {
        return Files.readAllBytes(recordDirectory.resolve("stdin.bin"));
    }

    public Path recordedWorkingDirectory() throws IOException {
        return Path.of(Files.readString(recordDirectory.resolve("cwd.txt")));
    }

    public List<String> recordedCallRootFiles() throws IOException {
        return MAPPER.readValue(recordDirectory.resolve("call-root.json").toFile(), new TypeReference<>() {
        });
    }

    public List<String> recordedWorkingDirectoryFiles() throws IOException {
        return MAPPER.readValue(recordDirectory.resolve("cwd-files.json").toFile(), new TypeReference<>() {
        });
    }

    /** Минимальное окружение «родительского» процесса для тестов: без настоящих ключей машины разработчика. */
    public static Map<String, String> parentEnvironment(Map<String, String> extra) {
        Map<String, String> environment = new HashMap<>();
        System.getenv().forEach((name, value) -> {
            String upper = name.toUpperCase(Locale.ROOT);
            if (upper.equals("PATH") || upper.equals("SYSTEMROOT") || upper.equals("WINDIR")
                    || upper.equals("PATHEXT") || upper.equals("COMSPEC") || upper.equals("SYSTEMDRIVE")) {
                environment.put(name, value);
            }
        });
        environment.putAll(extra);
        return environment;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }
}
