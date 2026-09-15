package ru.karandash.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import ru.karandash.agent.cli.AgentCli;
import ru.karandash.agent.cli.ClaudeCli;
import ru.karandash.agent.cli.CliAuthState;
import ru.karandash.agent.cli.CliProcessRunner;
import ru.karandash.agent.cli.CodexCli;
import ru.karandash.agent.security.ServiceTokenFilter;

@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class AgentConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AgentConfiguration.class);

    @Bean
    @ConditionalOnProperty(name = "karandash.agent.cli", havingValue = "claude", matchIfMissing = true)
    AgentCli claudeCli(AgentProperties properties, ObjectMapper objectMapper) {
        return new ClaudeCli(properties.claude(), objectMapper);
    }

    @Bean
    @ConditionalOnProperty(name = "karandash.agent.cli", havingValue = "codex")
    AgentCli codexCli(AgentProperties properties, ObjectMapper objectMapper) {
        log.info("Выбран codex exec. Инструменты выключены флагами features.*; при подписочном входе "
                + "каталог CODEX_HOME должен быть постоянным, иначе обновлённый токен будет теряться");
        return new CodexCli(properties.codex(), objectMapper);
    }

    @Bean
    CliProcessRunner cliProcessRunner() {
        return new CliProcessRunner(System.getenv());
    }

    @Bean
    CliAuthState cliAuthState() {
        return new CliAuthState();
    }

    @Bean
    FilterRegistrationBean<ServiceTokenFilter> serviceTokenFilter(AgentProperties properties) {
        if (properties.serviceToken() == null || properties.serviceToken().isBlank()) {
            log.warn("AGENT_SERVICE_TOKEN не задан — внутренний API агента отклоняет все запросы");
        }
        FilterRegistrationBean<ServiceTokenFilter> registration =
                new FilterRegistrationBean<>(new ServiceTokenFilter(properties.serviceToken()));
        registration.addUrlPatterns("/internal/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
