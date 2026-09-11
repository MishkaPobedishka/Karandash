package ru.karandash.agent.recognition;

public class AgentBusyException extends RuntimeException {

    public AgentBusyException() {
        super("Агент занят: превышен лимит параллельных вызовов");
    }
}
