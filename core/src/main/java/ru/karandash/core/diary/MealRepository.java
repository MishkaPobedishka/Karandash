package ru.karandash.core.diary;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

interface MealRepository extends JpaRepository<MealEntity, UUID> {

    List<MealEntity> findByAccountIdAndLocalDateOrderByLocalTime(UUID accountId, LocalDate localDate);
}
