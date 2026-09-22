package ru.karandash.core.canteen;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface PreferenceRepository extends JpaRepository<PreferenceEntity, UUID> {

    Optional<PreferenceEntity> findByAccountIdAndType(UUID accountId, String type);
}
