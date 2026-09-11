package ru.karandash.agent.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Пустой временный каталог одного вызова: рабочий каталог процесса, HOME, каталог настроек CLI и временные файлы.
 * Удаляется целиком при закрытии — вместе с фото, если CLI пришлось передать его файлом.
 */
public final class CallDirectory implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CallDirectory.class);

    private final Path root;

    private CallDirectory(Path root) {
        this.root = root;
    }

    public static CallDirectory create(Path base) throws IOException {
        Files.createDirectories(base);
        Path root = FileSystems.getDefault().supportedFileAttributeViews().contains("posix")
                ? Files.createTempDirectory(base, "call-",
                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
                : Files.createTempDirectory(base, "call-");
        CallDirectory directory = new CallDirectory(root);
        for (Path path : List.of(directory.home(), directory.config(), directory.tmp(), directory.work())) {
            Files.createDirectories(path);
        }
        return directory;
    }

    public Path root() {
        return root;
    }

    public Path home() {
        return root.resolve("home");
    }

    public Path config() {
        return root.resolve("config");
    }

    public Path tmp() {
        return root.resolve("tmp");
    }

    /** Рабочий каталог процесса — всегда пустой. */
    public Path work() {
        return root.resolve("work");
    }

    @Override
    public void close() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            if (deleteTree()) {
                return;
            }
            try {
                Thread.sleep(200L * attempt);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.warn("Не удалось полностью удалить каталог вызова {}", root.getFileName());
    }

    private boolean deleteTree() {
        if (!Files.exists(root)) {
            return true;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // повторим попытку целиком
                }
            });
        } catch (IOException ignored) {
            return false;
        }
        return !Files.exists(root);
    }
}
