package ru.karandash.agent.cli;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Запуск CLI: аргументы (без пользовательского ввода), stdin, переменные изоляции
 * и имена переменных родительского окружения, которые разрешено передать процессу (учётные данные CLI).
 */
public record CliInvocation(
        List<String> command,
        byte[] stdin,
        Map<String, String> environment,
        Set<String> passthroughVariables
) {

    public CliInvocation {
        command = List.copyOf(command);
        stdin = stdin == null ? new byte[0] : stdin;
        environment = Map.copyOf(environment);
        passthroughVariables = Set.copyOf(passthroughVariables);
    }
}
