package ru.karandash.agent.testing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Подменяет {@code claude} и {@code codex} в тестах: записывает, с чем его запустили, и печатает заготовленный вывод.
 * Режим задаётся {@code -Dfake.mode} или файлом {@code -Dfake.control}.
 */
public final class FakeCli {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final PrintStream OUT = new PrintStream(System.out, true, StandardCharsets.UTF_8);

    public static final String RECOGNITION_JSON = """
            {"items":[{"name":"Гречка с курицей","portion_g_min":250,"portion_g_max":320,"kcal_min":380,"kcal_max":480,\
            "protein_g_min":25,"protein_g_max":32,"fat_g_min":8,"fat_g_max":12,"carbs_g_min":45,"carbs_g_max":60,\
            "confidence":0.7}],"questions":[]}""";

    private FakeCli() {
    }

    public static void main(String[] args) throws Exception {
        Path record = Path.of(System.getProperty("fake.record"));
        Files.createDirectories(record);
        String mode = mode();
        byte[] stdin = System.in.readAllBytes();
        record(record, args, stdin);

        List<String> arguments = List.of(args);
        switch (mode) {
            case "claude-ok" -> {
                if (arguments.contains("auth")) {
                    OUT.println("{\"loggedIn\":true,\"authMethod\":\"api_key\"}");
                    return;
                }
                claudeInit(List.of());
                OUT.println("{\"type\":\"assistant\",\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}}");
                claudeResult(RECOGNITION_JSON);
            }
            case "claude-no-auth" -> {
                OUT.println("{\"loggedIn\":false,\"authMethod\":\"none\"}");
                System.exit(1);
            }
            case "claude-tools-in-init" -> {
                claudeInit(List.of("Bash", "Read"));
                sleep(30_000);
            }
            case "claude-tool-use" -> {
                claudeInit(List.of());
                OUT.println("{\"type\":\"assistant\",\"message\":{\"content\":[{\"type\":\"tool_use\",\"name\":\"Bash\","
                        + "\"input\":{\"command\":\"cat /etc/passwd\"}}]}}");
                sleep(30_000);
            }
            case "claude-auth-401" -> {
                claudeInit(List.of());
                OUT.println("{\"type\":\"system\",\"subtype\":\"api_retry\",\"attempt\":1,\"error_status\":401,"
                        + "\"error\":\"authentication_failed\"}");
                sleep(30_000);
            }
            case "claude-bad-json" -> {
                claudeInit(List.of());
                claudeResult("Это не JSON, а просто текст");
            }
            case "claude-contract-violation" -> {
                claudeInit(List.of());
                claudeResult("{\"items\":[{\"name\":\"Суп\",\"confidence\":0.5}],\"questions\":[]}");
            }
            case "codex-ok" -> {
                OUT.println("{\"type\":\"thread.started\",\"thread_id\":\"t-1\"}");
                ObjectNode item = MAPPER.createObjectNode().put("type", "item.completed");
                item.putObject("item").put("id", "i-1").put("type", "agent_message").put("text", RECOGNITION_JSON);
                OUT.println(MAPPER.writeValueAsString(item));
                OUT.println("{\"type\":\"turn.completed\",\"usage\":{\"input_tokens\":120,\"cached_input_tokens\":0,"
                        + "\"output_tokens\":60}}");
            }
            case "codex-shell" -> {
                OUT.println("{\"type\":\"item.started\",\"item\":{\"id\":\"i-1\",\"type\":\"command_execution\","
                        + "\"command\":\"cat /etc/passwd\"}}");
                sleep(30_000);
            }
            case "hang-with-child" -> {
                Process child = new ProcessBuilder(
                        ProcessHandle.current().info().command().orElseThrow(),
                        "-cp", System.getProperty("java.class.path"),
                        "-Dfake.mode=sleep",
                        "-Dfake.record=" + record.resolve("child"),
                        FakeCli.class.getName())
                        .redirectInput(ProcessBuilder.Redirect.PIPE)
                        .start();
                child.getOutputStream().close();
                Files.writeString(record.resolve("child.pid"), Long.toString(child.pid()));
                sleep(60_000);
            }
            case "sleep" -> sleep(60_000);
            case "flood" -> {
                String chunk = "x".repeat(8192);
                while (true) {
                    OUT.println(chunk);
                }
            }
            default -> {
                // режим echo: только записать, с чем запустили
            }
        }
    }

    private static String mode() throws IOException {
        String control = System.getProperty("fake.control");
        if (control != null && Files.exists(Path.of(control))) {
            return Files.readString(Path.of(control)).trim();
        }
        return System.getProperty("fake.mode", "echo");
    }

    private static void record(Path record, String[] args, byte[] stdin) throws IOException {
        MAPPER.writeValue(record.resolve("argv.json").toFile(), args);
        MAPPER.writeValue(record.resolve("env.json").toFile(), new TreeMap<>(System.getenv()));
        Files.write(record.resolve("stdin.bin"), stdin);
        Path cwd = Path.of("").toAbsolutePath();
        Files.writeString(record.resolve("cwd.txt"), cwd.toString());
        Path callRoot = cwd.getParent();
        try (Stream<Path> files = Files.list(callRoot)) {
            MAPPER.writeValue(record.resolve("call-root.json").toFile(),
                    files.map(path -> path.getFileName().toString()).sorted().toList());
        }
        try (Stream<Path> files = Files.list(cwd)) {
            MAPPER.writeValue(record.resolve("cwd-files.json").toFile(),
                    files.map(path -> path.getFileName().toString()).sorted().toList());
        }
    }

    private static void claudeInit(List<String> tools) throws IOException {
        ObjectNode init = MAPPER.createObjectNode()
                .put("type", "system")
                .put("subtype", "init")
                .put("model", "claude-test-model")
                .put("permissionMode", "dontAsk");
        init.set("tools", MAPPER.valueToTree(tools));
        init.set("mcp_servers", MAPPER.createArrayNode());
        OUT.println(MAPPER.writeValueAsString(init));
    }

    private static void claudeResult(String text) throws IOException {
        ObjectNode result = MAPPER.createObjectNode()
                .put("type", "result")
                .put("subtype", "success")
                .put("is_error", false)
                .put("result", text)
                .put("total_cost_usd", 0.0123);
        result.set("usage", MAPPER.valueToTree(Map.of(
                "input_tokens", 100,
                "cache_creation_input_tokens", 10,
                "cache_read_input_tokens", 5,
                "output_tokens", 50)));
        result.set("permission_denials", MAPPER.createArrayNode());
        OUT.println(MAPPER.writeValueAsString(result));
    }

    private static void sleep(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }
}
