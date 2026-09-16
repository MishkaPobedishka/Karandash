package ru.karandash.core.profile;

/** Текущее состояние разговора о телосложении: на каком шаге стоим и что уже названо. */
public record ProfileSetup(ProfileStep step, ProfileAnswers answers) {
}
