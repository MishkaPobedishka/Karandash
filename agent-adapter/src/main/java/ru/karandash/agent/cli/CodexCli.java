package ru.karandash.agent.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.karandash.agent.AgentProperties;
import ru.karandash.contracts.ai.ModelUsage;
import ru.karandash.contracts.ai.RecognitionContract;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * {@code codex exec}: промпт через stdin, фото — временным файлом в каталоге вызова ({@code --image}).
 * Флаги взяты из исходников codex-rs ({@code exec/src/cli.rs}, {@code features/src/lib.rs}) и проверены
 * на codex-cli 0.154.0: с ними модель отвечает по контракту, а на попытку выполнить команду из
 * пользовательского текста ни одного события запуска команд не появляется.
 */
public final class CodexCli implements AgentCli {

    public static final String PROVIDER = "codex-cli";

    static final List<String> DISABLED_FEATURES =
            List.of("shell_tool", "unified_exec", "view_image", "apps", "plugins", "multi_agent");

    private static final Set<String> CREDENTIAL_VARIABLES = Set.of("CODEX_API_KEY", "OPENAI_API_KEY");
    private static final Set<String> FORBIDDEN_ITEMS =
            Set.of("command_execution", "file_change", "mcp_tool_call", "web_search", "collab_tool_call");
    private static final String AUTH_FILE = "auth.json";
    private static final List<String> AUTH_FAILURE_MARKERS =
            List.of("401", "unauthorized", "invalid api key", "incorrect api key", "not logged in");

    private final AgentProperties.Codex settings;
    private final ObjectMapper objectMapper;

    public CodexCli(AgentProperties.Codex settings, ObjectMapper objectMapper) {
        if (settings == null || settings.command() == null || settings.command().isEmpty()) {
            throw new IllegalStateException("Не задан параметр karandash.agent.codex.command");
        }
        this.settings = settings;
        this.objectMapper = objectMapper;
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public Set<String> credentialVariables() {
        return CREDENTIAL_VARIABLES;
    }

    @Override
    public boolean hasFileCredentials() {
        return authFileReadable() || (settings.home() != null && Files.isReadable(settings.home().resolve(AUTH_FILE)));
    }

    @Override
    public CliInvocation prepare(RecognitionTask task, CallDirectory directory) throws IOException {
        List<String> command = new ArrayList<>(settings.command());
        command.addAll(List.of("exec", "--json", "--ephemeral", "--skip-git-repo-check", "--sandbox", "read-only"));
        if (hasModel()) {
            command.addAll(List.of("--model", settings.model()));
        }
        // Инструменты, которыми модель могла бы выполнять команды, читать файлы или ходить в сеть, выключены.
        DISABLED_FEATURES.forEach(feature -> command.addAll(List.of("-c", "features." + feature + "=false")));
        command.addAll(List.of("-c", "web_search='disabled'"));
        String request;
        if (task instanceof RecognitionTask.Photo photo) {
            Path image = directory.root().resolve("input." + photo.type().extension());
            Files.write(image, photo.bytes());
            command.addAll(List.of("--image", image.toString()));
            request = RecognitionContract.PHOTO_REQUEST;
        } else {
            request = Prompts.describeText(((RecognitionTask.Text) task).description());
        }
        command.add("-");
        byte[] prompt = (Prompts.systemPrompt(task) + "\n" + request + "\n").getBytes(StandardCharsets.UTF_8);
        return new CliInvocation(command, prompt, isolation(directory), CREDENTIAL_VARIABLES);
    }

    @Override
    public CliOutputHandler newOutputHandler() {
        return new OutputHandler();
    }

    @Override
    public CliInvocation healthCheck(CallDirectory directory) {
        List<String> command = new ArrayList<>(settings.command());
        command.addAll(List.of("login", "status"));
        return new CliInvocation(command, new byte[0], isolation(directory), CREDENTIAL_VARIABLES);
    }

    private Map<String, String> isolation(CallDirectory directory) {
        return Map.of("CODEX_HOME", codexHome(directory).toString());
    }

    /**
     * Каталог настроек CLI. Для подписки он постоянный: CLI сам обновляет там токен, и в одноразовом
     * каталоге обновление терялось бы, а старый токен после ротации перестал бы работать.
     */
    private Path codexHome(CallDirectory directory) {
        if (settings.home() == null) {
            return directory.config();
        }
        try {
            Files.createDirectories(settings.home());
            copyAuthFileIfAbsent(settings.home());
        } catch (IOException exception) {
            throw new CliCallException(CliCallException.Reason.START_FAILED,
                    "Не удалось подготовить каталог настроек CLI", exception);
        }
        return settings.home();
    }

    /** Кладём auth.json из секрета один раз: дальше файлом владеет сам CLI и обновляет его. */
    private void copyAuthFileIfAbsent(Path home) throws IOException {
        Path target = home.resolve(AUTH_FILE);
        if (Files.exists(target) || !authFileReadable()) {
            return;
        }
        Files.copy(settings.authFile(), target, StandardCopyOption.REPLACE_EXISTING);
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rw-------"));
        }
    }

    private boolean authFileReadable() {
        return settings.authFile() != null && Files.isReadable(settings.authFile());
    }

    private boolean hasModel() {
        return settings.model() != null && !settings.model().isBlank();
    }

    private final class OutputHandler implements CliOutputHandler {

        private String lastMessage;
        private boolean turnCompleted;
        private String failure;
        private Integer inputTokens;
        private Integer outputTokens;

        @Override
        public void onLine(String line) {
            JsonNode event = parse(line);
            if (event == null) {
                return;
            }
            switch (event.path("type").asText()) {
                case "item.started", "item.updated", "item.completed" -> onItem(event);
                case "turn.completed" -> {
                    turnCompleted = true;
                    JsonNode usage = event.path("usage");
                    inputTokens = usage.hasNonNull("input_tokens") ? usage.get("input_tokens").asInt() : null;
                    outputTokens = usage.hasNonNull("output_tokens") ? usage.get("output_tokens").asInt() : null;
                }
                case "turn.failed" -> failure = event.path("error").path("message").asText("");
                case "error" -> failure = event.path("message").asText("");
                default -> {
                }
            }
        }

        private void onItem(JsonNode event) {
            JsonNode item = event.path("item");
            String type = item.path("type").asText();
            if (FORBIDDEN_ITEMS.contains(type)) {
                throw new CliCallException(CliCallException.Reason.UNSAFE_BEHAVIOR,
                        "Модель попыталась вызвать инструмент (" + type + ")");
            }
            if ("item.completed".equals(event.path("type").asText()) && "agent_message".equals(type)) {
                lastMessage = item.path("text").asText("");
            }
        }

        @Override
        public CliReply complete(ProcessOutcome outcome) {
            if (failure != null) {
                String lower = failure.toLowerCase(Locale.ROOT);
                if (AUTH_FAILURE_MARKERS.stream().anyMatch(lower::contains)) {
                    throw new CliCallException(CliCallException.Reason.AUTH_REJECTED,
                            "Провайдер модели отклонил учётные данные CLI");
                }
                throw new CliCallException(CliCallException.Reason.FAILED, "CLI вернул ошибку");
            }
            if (!turnCompleted || lastMessage == null) {
                throw new CliCallException(CliCallException.Reason.FAILED,
                        "CLI завершился без результата, код выхода " + outcome.exitCode());
            }
            return new CliReply(lastMessage, new ModelUsage(PROVIDER, hasModel() ? settings.model() : null,
                    inputTokens, outputTokens, null));
        }
    }

    private JsonNode parse(String line) {
        if (line.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(line);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }
}
