package ru.karandash.core.diary;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.karandash.contracts.ai.RecognitionItem;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Дневник питания. Каждая запись привязана к аккаунту, и читается дневник только по аккаунту —
 * чужие записи достать нечем.
 */
@Service
@EnableConfigurationProperties(DiaryProperties.class)
public class DiaryService {

    private static final int MAX_TITLE_LENGTH = 120;

    private final MealRepository meals;
    private final FoodItemRepository foodItems;
    private final DiaryProperties properties;
    private final Clock clock;

    public DiaryService(MealRepository meals, FoodItemRepository foodItems, DiaryProperties properties, Clock clock) {
        this.meals = meals;
        this.foodItems = foodItems;
        this.properties = properties;
        this.clock = clock;
    }

    /** Записывает подтверждённую оценку и возвращает дневник за день этой записи. */
    @Transactional
    public DiaryDay record(MealDraft draft) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), properties.zone());
        MealEntity meal = new MealEntity(draft.accountId(), now.toLocalDate(), now.toLocalTime(),
                draft.source(), draft.inputText());
        List<RecognitionItem> items = draft.result().items();
        meal.setNutrition(
                sumInt(items, RecognitionItem::kcalMin),
                sumInt(items, RecognitionItem::kcalMax),
                sum(items, RecognitionItem::proteinGramsMin),
                sum(items, RecognitionItem::proteinGramsMax),
                sum(items, RecognitionItem::fatGramsMin),
                sum(items, RecognitionItem::fatGramsMax),
                sum(items, RecognitionItem::carbsGramsMin),
                sum(items, RecognitionItem::carbsGramsMax));
        meals.save(meal);
        foodItems.saveAll(items.stream().map(item -> new FoodItemEntity(meal.getId(), item)).toList());
        return day(draft.accountId(), now.toLocalDate());
    }

    /**
     * Удаляет запись дневника. Позиции внутри уходят каскадом самой базы.
     * Серию дней это не сбрасывает: день уже был засчитан, отбирать его за исправление нечестно.
     */
    @Transactional
    public DiaryDay delete(UUID accountId, UUID mealId) {
        meals.findByIdAndAccountId(mealId, accountId).ifPresent(meals::delete);
        return today(accountId);
    }

    /** Пересчитывает запись: половина порции, двойная — что угодно, лишь бы множитель был разумным. */
    @Transactional
    public DiaryDay scale(UUID accountId, UUID mealId, BigDecimal factor) {
        meals.findByIdAndAccountId(mealId, accountId).ifPresent(meal -> {
            meal.scale(factor);
            meals.save(meal);
            List<FoodItemEntity> items = foodItems.findByMealId(mealId);
            items.forEach(item -> item.scale(factor));
            foodItems.saveAll(items);
        });
        return today(accountId);
    }

    @Transactional(readOnly = true)
    public DiaryDay today(UUID accountId) {
        return day(accountId, LocalDate.ofInstant(clock.instant(), properties.zone()));
    }

    private DiaryDay day(UUID accountId, LocalDate date) {
        List<MealEntity> dayMeals = meals.findByAccountIdAndLocalDateOrderByLocalTime(accountId, date);
        Map<UUID, List<FoodItemEntity>> itemsByMeal = dayMeals.isEmpty()
                ? Map.of()
                : foodItems.findByMealIdIn(dayMeals.stream().map(MealEntity::getId).toList()).stream()
                        .collect(Collectors.groupingBy(FoodItemEntity::getMealId));
        List<DiaryDay.Entry> entries = new ArrayList<>();
        int kcalMin = 0;
        int kcalMax = 0;
        for (MealEntity meal : dayMeals) {
            entries.add(new DiaryDay.Entry(meal.getId(), meal.getLocalTime(),
                    title(meal, itemsByMeal.get(meal.getId())),
                    meal.getKcalMin(), meal.getKcalMax()));
            kcalMin += meal.getKcalMin() == null ? 0 : meal.getKcalMin();
            kcalMax += meal.getKcalMax() == null ? 0 : meal.getKcalMax();
        }
        return new DiaryDay(date, List.copyOf(entries), kcalMin, kcalMax);
    }

    private static String title(MealEntity meal, List<FoodItemEntity> items) {
        String title = items == null || items.isEmpty()
                ? meal.getInputText()
                : items.stream().map(FoodItemEntity::getName).collect(Collectors.joining(", "));
        if (title == null || title.isBlank()) {
            title = "еда";
        }
        return title.length() <= MAX_TITLE_LENGTH ? title : title.substring(0, MAX_TITLE_LENGTH - 1) + "…";
    }

    private static Integer sumInt(List<RecognitionItem> items, Function<RecognitionItem, Integer> value) {
        return items.stream().map(value).filter(Objects::nonNull).reduce(Integer::sum).orElse(null);
    }

    private static BigDecimal sum(List<RecognitionItem> items, Function<RecognitionItem, BigDecimal> value) {
        return items.stream().map(value).filter(Objects::nonNull).reduce(BigDecimal::add).orElse(null);
    }
}
