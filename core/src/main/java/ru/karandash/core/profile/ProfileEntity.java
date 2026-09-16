package ru.karandash.core.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import ru.karandash.core.calc.GoalDirection;
import ru.karandash.core.calc.Sex;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "profile")
public class ProfileEntity {

    @Id
    private UUID accountId;

    @Column(nullable = false)
    private BigDecimal weightKg;

    @Column(nullable = false)
    private BigDecimal heightCm;

    @Column(nullable = false)
    private int age;

    @Column(nullable = false)
    private String sex;

    @Column(nullable = false)
    private BigDecimal activityLevel;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ProfileEntity() {
    }

    ProfileEntity(UUID accountId) {
        this.accountId = accountId;
    }

    void fill(ProfileAnswers answers) {
        this.weightKg = answers.weightKg();
        this.heightCm = BigDecimal.valueOf(answers.heightCm());
        this.age = answers.age();
        this.sex = answers.sex().name();
        // Колонка хранит два знака после запятой, поэтому 1.375 округлится — при чтении ищем ближайший уровень.
        this.activityLevel = answers.activity().coefficient().setScale(2, java.math.RoundingMode.HALF_UP);
        this.updatedAt = Instant.now();
    }

    ProfileAnswers toAnswers(GoalDirection goal) {
        return new ProfileAnswers(Sex.valueOf(sex), age, heightCm.intValue(), weightKg, activity(), goal);
    }

    private ActivityLevel activity() {
        ActivityLevel nearest = ActivityLevel.SEDENTARY;
        BigDecimal smallestGap = null;
        for (ActivityLevel level : ActivityLevel.values()) {
            BigDecimal gap = level.coefficient().subtract(activityLevel).abs();
            if (smallestGap == null || gap.compareTo(smallestGap) < 0) {
                smallestGap = gap;
                nearest = level;
            }
        }
        return nearest;
    }
}
