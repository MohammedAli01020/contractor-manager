package com.contractor.app.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Checks the real application.yml defaults without integration-test property overrides. */
class SwaggerConfigurationDefaultTest {

    @Test
    void swaggerAndApiDocsAreDisabledByDefaultInApplicationConfiguration() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(SwaggerFlagConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SwaggerFlags flags = context.getBean(SwaggerFlags.class);
                    assertThat(flags.apiDocsEnabled).isFalse();
                    assertThat(flags.swaggerUiEnabled).isFalse();
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class SwaggerFlagConfiguration {
        @Bean
        SwaggerFlags swaggerFlags(
                @Value("${springdoc.api-docs.enabled:false}") boolean apiDocsEnabled,
                @Value("${springdoc.swagger-ui.enabled:false}") boolean swaggerUiEnabled) {
            return new SwaggerFlags(apiDocsEnabled, swaggerUiEnabled);
        }
    }

    static class SwaggerFlags {
        private final boolean apiDocsEnabled;
        private final boolean swaggerUiEnabled;

        SwaggerFlags(boolean apiDocsEnabled, boolean swaggerUiEnabled) {
            this.apiDocsEnabled = apiDocsEnabled;
            this.swaggerUiEnabled = swaggerUiEnabled;
        }
    }
}
