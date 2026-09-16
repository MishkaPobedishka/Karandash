package ru.karandash.core.changelog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Запись «что нового»: пишет администратор, он же рассылает. */
@Entity
@Table(name = "changelog_entry")
public class ChangelogEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String body;

    private UUID createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant publishedAt;

    private Integer recipients;

    protected ChangelogEntity() {
    }

    ChangelogEntity(String title, String body, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.title = title;
        this.body = body;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Integer getRecipients() {
        return recipients;
    }

    void publish(int recipients) {
        this.publishedAt = Instant.now();
        this.recipients = recipients;
    }
}
