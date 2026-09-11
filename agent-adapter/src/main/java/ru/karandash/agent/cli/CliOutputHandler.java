package ru.karandash.agent.cli;

/**
 * Разбор вывода одного вызова. {@link #onLine} вызывается на каждую строку stdout и может прервать вызов,
 * бросив {@link CliCallException}, — тогда процесс убивается сразу.
 */
public interface CliOutputHandler {

    void onLine(String line);

    CliReply complete(ProcessOutcome outcome);
}
