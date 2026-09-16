package ru.karandash.core.profile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.karandash.core.calc.GoalDirection;
import ru.karandash.core.calc.MifflinStJeorCalculator;
import ru.karandash.core.calc.NutritionCalculationRequest;
import ru.karandash.core.calc.NutritionTarget;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Телосложение, цель и посчитанная по ним норма калорий.
 * Формула — Миффлин — Сан Жеор: основной обмен по росту, весу, возрасту и полу, дальше поправка на активность
 * и на цель (сбросить, держать, набрать).
 */
@Service
public class ProfileService {

    /** Насколько отклоняемся от расхода: минус на похудении, плюс на наборе. */
    private static final BigDecimal LOSS_ADJUSTMENT = new BigDecimal("0.15");
    private static final BigDecimal GAIN_ADJUSTMENT = new BigDecimal("0.10");

    private final ProfileRepository profiles;
    private final GoalRepository goals;
    private final ProfileSetupRepository setups;
    private final MifflinStJeorCalculator calculator;
    private final ObjectMapper objectMapper;

    public ProfileService(
            ProfileRepository profiles,
            GoalRepository goals,
            ProfileSetupRepository setups,
            MifflinStJeorCalculator calculator,
            ObjectMapper objectMapper
    ) {
        this.profiles = profiles;
        this.goals = goals;
        this.setups = setups;
        this.calculator = calculator;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Optional<ProfileSetup> activeSetup(UUID accountId) {
        return setups.findById(accountId)
                .map(entity -> new ProfileSetup(entity.getStep(), read(entity.getData())));
    }

    @Transactional
    public ProfileSetup startSetup(UUID accountId) {
        ProfileSetup setup = new ProfileSetup(ProfileStep.SEX, ProfileAnswers.empty());
        store(accountId, setup);
        return setup;
    }

    @Transactional
    public ProfileSetup saveSetup(UUID accountId, ProfileSetup setup) {
        store(accountId, setup);
        return setup;
    }

    @Transactional
    public void cancelSetup(UUID accountId) {
        setups.deleteById(accountId);
    }

    /** Считает норму, сохраняет телосложение и цель, заканчивает разговор. */
    @Transactional
    public ProfileSummary complete(UUID accountId, ProfileAnswers answers) {
        NutritionTarget target = calculate(answers);
        ProfileEntity profile = profiles.findById(accountId).orElseGet(() -> new ProfileEntity(accountId));
        profile.fill(answers);
        profiles.saveAndFlush(profile);
        goals.findByAccountIdAndStatus(accountId, GoalEntity.STATUS_ACTIVE).forEach(GoalEntity::replace);
        goals.flush();
        goals.saveAndFlush(new GoalEntity(accountId, answers.goal(), target));
        setups.deleteById(accountId);
        return new ProfileSummary(answers, target);
    }

    @Transactional(readOnly = true)
    public Optional<ProfileSummary> current(UUID accountId) {
        return goals.findFirstByAccountIdAndStatusOrderByCreatedAtDesc(accountId, GoalEntity.STATUS_ACTIVE)
                .flatMap(goal -> profiles.findById(accountId)
                        .map(profile -> profile.toAnswers(goal.direction()))
                        .map(answers -> new ProfileSummary(answers, calculate(answers))));
    }

    /** Норма на день, если пользователь её посчитал. */
    @Transactional(readOnly = true)
    public Optional<DailyTarget> dailyTarget(UUID accountId) {
        return goals.findFirstByAccountIdAndStatusOrderByCreatedAtDesc(accountId, GoalEntity.STATUS_ACTIVE)
                .map(goal -> new DailyTarget(goal.getKcalMin(), goal.getKcalMax(), goal.direction()));
    }

    private NutritionTarget calculate(ProfileAnswers answers) {
        return calculator.calculate(new NutritionCalculationRequest(
                answers.weightKg(),
                BigDecimal.valueOf(answers.heightCm()),
                answers.age(),
                answers.sex(),
                answers.activity().coefficient(),
                answers.goal(),
                adjustment(answers.goal())));
    }

    private static BigDecimal adjustment(GoalDirection goal) {
        return switch (goal) {
            case LOSS -> LOSS_ADJUSTMENT;
            case MAINTENANCE -> BigDecimal.ZERO;
            case GAIN -> GAIN_ADJUSTMENT;
        };
    }

    private void store(UUID accountId, ProfileSetup setup) {
        String data = write(setup.answers());
        setups.findById(accountId).ifPresentOrElse(
                entity -> entity.update(setup.step(), data),
                () -> setups.saveAndFlush(new ProfileSetupEntity(accountId, setup.step(), data)));
    }

    private String write(ProfileAnswers answers) {
        try {
            return objectMapper.writeValueAsString(answers);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Ответы о телосложении не сериализуются", exception);
        }
    }

    private ProfileAnswers read(String json) {
        try {
            return objectMapper.readValue(json, ProfileAnswers.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Сохранённые ответы о телосложении не читаются", exception);
        }
    }
}
