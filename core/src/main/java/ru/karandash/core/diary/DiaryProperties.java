package ru.karandash.core.diary;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.ZoneId;

/**
 * @param zone            часовой пояс, в котором считается «день» дневника, пока у аккаунта нет своего
 * @param draftRetention  через сколько забывается оценка, на которую пользователь так и не ответил
 */
@ConfigurationProperties(prefix = "karandash.diary")
public record DiaryProperties(
        ZoneId zone,
        Duration draftRetention
) {

    public DiaryProperties {
        zone = zone == null ? ZoneId.of("Asia/Yekaterinburg") : zone;
        draftRetention = draftRetention == null ? Duration.ofHours(24) : draftRetention;
    }
}
