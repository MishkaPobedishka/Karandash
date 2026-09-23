package ru.karandash.core.diary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Оценка, показанная пользователю, но ещё не записанная в дневник.
 */
@Entity
@Table(name = "meal_draft")
public class MealDraftEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID accountId;

    @Column(nullable = false)
    private String inputSource;

    private String inputText;

    @Column(nullable = false)
    private String resultJson;

    @Column(nullable = false)
    private int revision;

    /** Вопросы модели и ответы человека по этой еде, по строке на реплику. */
    private String dialog;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected MealDraftEntity() {
    }

    MealDraftEntity(UUID accountId, DraftSource source, String inputText, String resultJson, int revision,
            String dialog) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.inputSource = source.name();
        this.inputText = inputText;
        this.resultJson = resultJson;
        this.revision = revision;
        this.dialog = dialog;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public DraftSource getSource() {
        return DraftSource.valueOf(inputSource);
    }

    public String getInputText() {
        return inputText;
    }

    public String getDialog() {
        return dialog;
    }

    public String getResultJson() {
        return resultJson;
    }

    public int getRevision() {
        return revision;
    }
}
