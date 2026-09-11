package ru.karandash.contracts.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record RecognitionItem(
        String name,
        @JsonProperty("portion_g_min")
        BigDecimal portionGramsMin,
        @JsonProperty("portion_g_max")
        BigDecimal portionGramsMax,
        @JsonProperty("kcal_min")
        Integer kcalMin,
        @JsonProperty("kcal_max")
        Integer kcalMax,
        @JsonProperty("protein_g_min")
        BigDecimal proteinGramsMin,
        @JsonProperty("protein_g_max")
        BigDecimal proteinGramsMax,
        @JsonProperty("fat_g_min")
        BigDecimal fatGramsMin,
        @JsonProperty("fat_g_max")
        BigDecimal fatGramsMax,
        @JsonProperty("carbs_g_min")
        BigDecimal carbsGramsMin,
        @JsonProperty("carbs_g_max")
        BigDecimal carbsGramsMax,
        BigDecimal confidence
) {
}
