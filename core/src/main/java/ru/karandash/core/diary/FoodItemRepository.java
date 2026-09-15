package ru.karandash.core.diary;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface FoodItemRepository extends JpaRepository<FoodItemEntity, UUID> {

    List<FoodItemEntity> findByMealIdIn(List<UUID> mealIds);
}
