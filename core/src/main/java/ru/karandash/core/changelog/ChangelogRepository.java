package ru.karandash.core.changelog;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface ChangelogRepository extends JpaRepository<ChangelogEntity, UUID> {

    List<ChangelogEntity> findByPublishedAtNotNullOrderByPublishedAtDesc(Limit limit);

    List<ChangelogEntity> findByPublishedAtNullOrderByCreatedAt();
}
