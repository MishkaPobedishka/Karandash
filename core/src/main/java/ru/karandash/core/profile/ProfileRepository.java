package ru.karandash.core.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface ProfileRepository extends JpaRepository<ProfileEntity, UUID> {
}
