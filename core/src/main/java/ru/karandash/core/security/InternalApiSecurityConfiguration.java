package ru.karandash.core.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
@EnableConfigurationProperties(InternalApiProperties.class)
public class InternalApiSecurityConfiguration {

    private static final Logger log = LoggerFactory.getLogger(InternalApiSecurityConfiguration.class);

    @Bean
    FilterRegistrationBean<ServiceTokenFilter> serviceTokenFilter(InternalApiProperties properties) {
        if (properties.serviceToken() == null || properties.serviceToken().isBlank()) {
            log.warn("CORE_SERVICE_TOKEN не задан — внутренний API ядра отклоняет все запросы");
        }
        FilterRegistrationBean<ServiceTokenFilter> registration =
                new FilterRegistrationBean<>(new ServiceTokenFilter(properties.serviceToken()));
        registration.addUrlPatterns("/internal/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
