package ru.karandash.agent.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.karandash.agent.AgentProperties;
import ru.karandash.contracts.ai.ModelUsage;
import ru.karandash.contracts.ai.RecognitionContract;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * {@code claude -p}: один вызов — один процесс, ввод через stdin в формате stream-json.
 * Флаги сверены с {@code claude --help} версии 2.1.268.
 */
public final class ClaudeCli implements AgentCli {

    public static final String PROVIDER = "claude-cli";

    private static final Set<String> CREDENTIAL_VARIABLES = Set.of("ANTHROPIC_API_KEY", "CLAUDE_CODE_OAUTH_TOKEN");
    private static final Set<String> EFFORT_LEVELS = Set.of("low", "medium", "high", "xhigh", "max");
    private static final List<String> AUTH_FAILURE_MARKERS =
            List.of("authentication_error", "authentication_failed", "invalid api key", "invalid x-api-key",
                    "oauth token has expired", "please run /login");

    private final AgentProperties.Claude settings;
    private final ObjectMapper objectMapper;

    public ClaudeCli(AgentProperties.Claude settings, ObjectMapper objectMapper) {
        if (settings == null || settings.command() == null || settings.command().isEmpty()) {
            throw new IllegalStateException("Не задан параметр karandash.agent.claude.command");
        }
        if (settings.model() == null || settings.model().isBlank()) {
            throw new IllegalStateException("Не задан параметр karandash.agent.claude.model");
        }
        if (settings.effort() != null && !settings.effort().isBlank() && !EFFORT_LEVELS.contains(settings.effort())) {
            throw new IllegalStateException("karandash.agent.claude.effort должен быть одним из " + EFFORT_LEVELS);
        }
        if (settings.maxBudgetUsd() == null || settings.maxBudgetUsd().signum() <= 0) {
            throw new IllegalStateException("karandash.agent.claude.max-budget-usd должен быть больше нуля");
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
    public CliInvocation prepare(RecognitionTask task, CallDirectory directory) throws IOException {
        Path systemPrompt = Files.writeString(directory.root().resolve("system-prompt.txt"), Prompts.systemPrompt(task));
        Path mcpConfig = Files.writeString(directory.root().resolve("mcp.json"), "{\"mcpServers\":{}}");
        List<String> command = new ArrayList<>(settings.command());
        command.addAll(List.of(
                "-p",
                "--input-format", "stream-json",
                "--output-format", "stream-json",
                "--verbose",
                "--model", settings.model(),
                "--system-prompt-file", systemPrompt.toString(),
                // Встроенные инструменты выключены полностью.
                "--tools", "",
                // Только пустой список MCP-серверов из файла, остальные конфигурации MCP игнорируются.
                "--strict-mcp-config",
                "--mcp-config", mcpConfig.toString(),
                // Без CLAUDE.md, навыков, плагинов, хуков и пользовательских настроек.
                "--safe-mode",
                "--restricted",
                "--disable-slash-commands",
                "--no-session-persistence",
                // Всё, что не разрешено заранее, отклоняется без вопросов.
                "--permission-mode", "dontAsk",
                "--permission-prompts", "none",
                "--max-budget-usd", settings.maxBudgetUsd().toPlainString()));
        if (settings.effort() != null && !settings.effort().isBlank()) {
            command.addAll(List.of("--effort", settings.effort()));
        }
        return new CliInvocation(command, userMessage(task), isolation(directory), CREDENTIAL_VARIABLES);
    }

    @Override
    public CliOutputHandler newOutputHandler() {
        return new OutputHandler();
    }

    @Override
    public CliInvocation healthCheck(CallDirectory directory) {
        List<String> command = new ArrayList<>(settings.command());
        command.addAll(List.of("auth", "status", "--json"));
        return new CliInvocation(command, new byte[0], isolation(directory), CREDENTIAL_VARIABLES);
    }

    private Map<String, String> isolation(CallDirectory directory) {
        return Map.of(
                "CLAUDE_CONFIG_DIR", directory.config().toString(),
                "DISABLE_AUTOUPDATER", "1",
                "DISABLE_TELEMETRY", "1",
                "DISABLE_ERROR_REPORTING", "1",
                "CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC", "1"
        );
    }

    byte[] userMessage(RecognitionTask task) throws JsonProcessingException {
        ArrayNode content = objectMapper.createArrayNode();
        switch (task) {
            case RecognitionTask.Text text -> content.addObject()
                    .put("type", "text")
                    .put("text", Prompts.describeText(text.description()));
            case RecognitionTask.Photo photo -> {
                ObjectNode image = content.addObject().put("type", "image");
                image.putObject("source")
                        .put("type", "base64")
                        .put("media_type", photo.type().mediaType())
                        .put("data", Base64.getEncoder().encodeToString(photo.bytes()));
                content.addObject()
                        .put("type", "text")
                        .put("text", RecognitionContract.PHOTO_REQUEST);
            }
            case RecognitionTask.Lunch lunch -> content.addObject()
                    .put("type", "text")
                    .put("text", lunch.prompt());
        }
        ObjectNode line = objectMapper.createObjectNode().put("type", "user");
        line.putObject("message").put("role", "user").set("content", content);
        return (objectMapper.writeValueAsString(line) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private final class OutputHandler implements CliOutputHandler {

        private boolean initVerified;
        private String model;
        private JsonNode result;

        @Override
        public void onLine(String line) {
            JsonNode event = parse(line);
            if (event == null) {
                return;
            }
            switch (event.path("type").asText()) {
                case "system" -> onSystem(event);
                case "assistant" -> rejectToolUse(event.path("message").path("content"));
                case "result" -> result = event;
                default -> {
                }
            }
        }

        private void onSystem(JsonNode event) {
            switch (event.path("subtype").asText()) {
                case "init" -> {
                    if (!isEmptyArray(event.get("tools")) || !isEmptyArray(event.get("mcp_servers"))) {
                        throw new CliCallException(CliCallException.Reason.UNSAFE_BEHAVIOR,
                                "CLI запустился с инструментами или MCP-серверами");
                    }
                    initVerified = true;
                    model = event.path("model").asText(null);
                }
                case "api_retry" -> {
                    int status = event.path("error_status").asInt();
                    if (status == 401 || status == 403) {
                        throw new CliCallException(CliCallException.Reason.AUTH_REJECTED,
                                "Провайдер модели отклонил учётные данные CLI (" + status + ")");
                    }
                }
                default -> {
                }
            }
        }

        private void rejectToolUse(JsonNode content) {
            if (!content.isArray()) {
                return;
            }
            for (JsonNode block : content) {
                if (block.path("type").asText().endsWith("tool_use")) {
                    throw new CliCallException(CliCallException.Reason.UNSAFE_BEHAVIOR,
                            "Модель попыталась вызвать инструмент");
                }
            }
        }

        @Override
        public CliReply complete(ProcessOutcome outcome) {
            if (!initVerified) {
                throw new CliCallException(CliCallException.Reason.FAILED,
                        "CLI не подтвердил запуск без инструментов, код выхода " + outcome.exitCode());
            }
            if (result == null) {
                throw new CliCallException(CliCallException.Reason.FAILED,
                        "CLI завершился без результата, код выхода " + outcome.exitCode());
            }
            JsonNode denials = result.get("permission_denials");
            if (denials != null && denials.isArray() && !denials.isEmpty()) {
                throw new CliCallException(CliCallException.Reason.UNSAFE_BEHAVIOR,
                        "Модель пыталась вызвать инструмент");
            }
            String subtype = result.path("subtype").asText("unknown");
            if (result.path("is_error").asBoolean(false) || !"success".equals(subtype)) {
                if (looksLikeAuthFailure(result.path("result").asText(""))) {
                    throw new CliCallException(CliCallException.Reason.AUTH_REJECTED,
                            "Провайдер модели отклонил учётные данные CLI");
                }
                throw new CliCallException(CliCallException.Reason.FAILED, "CLI вернул ошибку: " + subtype);
            }
            JsonNode usage = result.path("usage");
            return new CliReply(
                    result.path("result").asText(""),
                    new ModelUsage(
                            PROVIDER,
                            model,
                            sum(usage, "input_tokens", "cache_creation_input_tokens", "cache_read_input_tokens"),
                            usage.hasNonNull("output_tokens") ? usage.get("output_tokens").asInt() : null,
                            result.hasNonNull("total_cost_usd") ? new BigDecimal(result.get("total_cost_usd").asText()) : null
                    )
            );
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

    private static boolean isEmptyArray(JsonNode node) {
        return node != null && node.isArray() && node.isEmpty();
    }

    private static Integer sum(JsonNode usage, String... fields) {
        Integer total = null;
        for (String field : fields) {
            if (usage.hasNonNull(field)) {
                total = (total == null ? 0 : total) + usage.get(field).asInt();
            }
        }
        return total;
    }

    private static boolean looksLikeAuthFailure(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return AUTH_FAILURE_MARKERS.stream().anyMatch(lower::contains);
    }
}
