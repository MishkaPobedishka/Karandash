package ru.karandash.core.canteen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Разбор ответа столовой — на куске настоящего меню ЦО.
 */
class CanteenClientTest {

    private static final String MENU_URL =
            "https://api-gateway.test/api/v1/canteen/menu/?cafe=1";

    private final CanteenProperties properties = new CanteenProperties(
            true, "https://api-gateway.test", 1, "test-token", Duration.ofSeconds(5), null,
            Duration.ofMinutes(10), null, null);

    @Test
    void readsDishesAndSkipsDrinks() throws IOException {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(MENU_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess(sample(), MediaType.APPLICATION_JSON));

        Optional<CanteenMenu> menu = new CanteenClient(properties, builder).today();

        server.verify();
        assertThat(menu).isPresent();
        List<CanteenDish> dishes = menu.get().dishes();
        assertThat(dishes).extracting(CanteenDish::category)
                .as("напитки в подбор обеда не идут")
                .doesNotContain("Холодные напитки");
        CanteenDish olivier = menu.get().find("салат оливье").orElseThrow();
        assertThat(olivier.price()).isEqualByComparingTo("60");
        assertThat(olivier.weightGrams()).isEqualByComparingTo("110");
        assertThat(olivier.kcal()).isEqualByComparingTo("163.8");
        assertThat(olivier.proteins()).isEqualByComparingTo("6.3");
        assertThat(olivier.hasNutrition()).isTrue();
        // Часть блюд столовая отдаёт без пищевой ценности — это нормально, их оценит модель.
        assertThat(menu.get().find("Айсберг").orElseThrow().hasNutrition()).isFalse();
        assertThat(menu.get().find("такого блюда нет")).isEmpty();
        assertThat(olivier.photoUrl()).as("для фотографии берём полный снимок, а не миниатюру")
                .isEqualTo("https://storage.test/products/olivier.jpg?X-Amz-Signature=full");
        CanteenDish aisberg = menu.get().find("Айсберг").orElseThrow();
        assertThat(aisberg.photoUrl()).as("без полного снимка годится миниатюра")
                .isEqualTo("https://storage.test/products/thumbs/aisberg.jpg?X-Amz-Signature=thumb");
        assertThat(menu.get().find("картофельное пюре").orElseThrow().hasPhoto())
                .as("столовая снимает не все блюда").isFalse();
    }

    @Test
    void asksCanteenOnceAndServesTheMenuFromMemory() throws IOException {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(ExpectedCount.once(), requestTo(MENU_URL))
                .andRespond(withSuccess(sample(), MediaType.APPLICATION_JSON));
        CanteenClient client = new CanteenClient(properties, builder);

        assertThat(client.today()).isPresent();
        assertThat(client.today()).as("второй раз меню берём из памяти").isPresent();

        server.verify();
    }

    @Test
    void fingerprintChangesWhenTheCanteenChangesTheMenu() throws IOException {
        CanteenClient client = new CanteenClient(properties, RestClient.builder());
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode body = objectMapper.readTree(sample());
        CanteenMenu menu = client.parse(body);

        ((com.fasterxml.jackson.databind.node.ObjectNode) body.path("result").path("categories").get(0)
                .path("products").get(0)).put("price", 999);
        CanteenMenu changed = client.parse(body);

        assertThat(menu.fingerprint()).isEqualTo(client.parse(objectMapper.readTree(sample())).fingerprint());
        assertThat(changed.fingerprint()).isNotEqualTo(menu.fingerprint());
    }

    @Test
    void canteenIsClosedOnWeekends() {
        assertThat(properties.workday(LocalDate.of(2026, 9, 25))).as("пятница").isTrue();
        assertThat(properties.workday(LocalDate.of(2026, 9, 26))).as("суббота").isFalse();
        assertThat(properties.workday(LocalDate.of(2026, 9, 27))).as("воскресенье").isFalse();
        CanteenProperties always = new CanteenProperties(
                true, null, 1, "t", Duration.ofSeconds(5), null, null, null, false);
        assertThat(always.workday(LocalDate.of(2026, 9, 26))).as("настройкой выходные можно включить").isTrue();
    }

    @Test
    void survivesBrokenApi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(MENU_URL)).andRespond(withServerError());

        assertThat(new CanteenClient(properties, builder).today())
                .as("сбой столовой не должен ронять рассылку").isEmpty();
    }

    @Test
    void staysQuietWithoutToken() {
        CanteenProperties noToken = new CanteenProperties(
                true, null, 1, "", Duration.ofSeconds(5), null, null, null, null);

        assertThat(new CanteenClient(noToken, RestClient.builder()).today()).isEmpty();
    }

    @Test
    void ignoresUnexpectedBody() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode body = objectMapper.readTree("{\"result\":{\"categories\":[{\"name\":\"Супы\",\"products\":[]}]}}");

        assertThat(new CanteenClient(properties, RestClient.builder()).parse(body).isEmpty()).isTrue();
        assertThat(new CanteenClient(properties, RestClient.builder()).parse(null).isEmpty()).isTrue();
    }

    private static String sample() throws IOException {
        try (InputStream stream = CanteenClientTest.class.getResourceAsStream("/canteen-menu-sample.json")) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
