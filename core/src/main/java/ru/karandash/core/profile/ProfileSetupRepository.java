package ru.karandash.core.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface ProfileSetupRepository extends JpaRepository<ProfileSetupEntity, UUID> {
}
