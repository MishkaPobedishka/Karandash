package ru.karandash.core.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class RecognitionModelConfiguration {

    @Bean
    @ConditionalOnProperty(name = "karandash.ai.enabled", havingValue = "true")
    OpenAiCompatibleRecognitionModel openAiCompatibleRecognitionModel(
            AiProperties properties,
            RestClient.Builder restClientBuilder,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper
    ) {
        return new OpenAiCompatibleRecognitionModel(properties, restClientBuilder, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(RecognitionModel.class)
    RecognitionModel disabledRecognitionModel() {
        return new DisabledRecognitionModel();
    }
}

