package ru.karandash.core.diary;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.karandash.contracts.ai.RecognitionResult;

import java.util.Optional;
import java.util.UUID;

/**
 * Хранит последнюю показанную оценку пользователя, пока он не решит, что с ней делать.
 * На аккаунт — одна: новая оценка вытесняет прошлую.
 */
@Service
@EnableConfigurationProperties(DiaryProperties.class)
public class MealDraftService {

    private final MealDraftRepository drafts;
    private final ObjectMapper objectMapper;

    public MealDraftService(MealDraftRepository drafts, ObjectMapper objectMapper) {
        this.drafts = drafts;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public MealDraft save(UUID accountId, DraftSource source, String inputText, RecognitionResult result, int revision) {
        drafts.deleteByAccountId(accountId);
        drafts.flush();
        MealDraftEntity saved = drafts.save(new MealDraftEntity(accountId, source, inputText, write(result), revision));
        return new MealDraft(saved.getId(), accountId, source, inputText, result, revision);
    }

    @Transactional(readOnly = true)
    public Optional<MealDraft> findActive(UUID accountId) {
        return drafts.findByAccountId(accountId).map(this::toDraft);
    }

    @Transactional(readOnly = true)
    public Optional<MealDraft> find(UUID accountId, UUID draftId) {
        return drafts.findByIdAndAccountId(draftId, accountId).map(this::toDraft);
    }

    @Transactional
    public void discard(UUID accountId) {
        drafts.deleteByAccountId(accountId);
    }

    private MealDraft toDraft(MealDraftEntity entity) {
        return new MealDraft(entity.getId(), entity.getAccountId(), entity.getSource(), entity.getInputText(),
                read(entity.getResultJson()), entity.getRevision());
    }

    public String write(RecognitionResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Оценка не сериализуется", exception);
        }
    }

    private RecognitionResult read(String json) {
        try {
            return objectMapper.readValue(json, RecognitionResult.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Сохранённая оценка не читается", exception);
        }
    }
}
