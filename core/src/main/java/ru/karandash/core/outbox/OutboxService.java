package ru.karandash.core.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class OutboxService {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public UUID append(String aggregateType, UUID aggregateId, String eventType, Object payload) {
        UUID eventId = UUID.randomUUID();
        repository.save(new OutboxEventEntity(
                eventId,
                aggregateType,
                aggregateId,
                eventType,
                objectMapper.valueToTree(payload)
        ));
        return eventId;
    }
}

