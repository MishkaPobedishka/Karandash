package ru.karandash.agent.cli;

import java.io.IOException;
import java.util.Set;

/**
 * CLI-агент, который запускается отдельным процессом на каждый вызов.
 */
public interface AgentCli {

    /** Имя провайдера для учёта вызовов. */
    String provider();

    /** Имена переменных окружения с учётными данными, которые передаются процессу. */
    Set<String> credentialVariables();

    CliInvocation prepare(RecognitionTask task, CallDirectory directory) throws IOException;

    CliOutputHandler newOutputHandler();

    /** Локальная проверка для health: CLI запускается и видит учётные данные. Модель не вызывается. */
    CliInvocation healthCheck(CallDirectory directory);
}
