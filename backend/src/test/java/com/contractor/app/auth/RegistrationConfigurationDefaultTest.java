package com.contractor.app.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Checks the real application.yml default without the integration-test override that enables registration. */
class RegistrationConfigurationDefaultTest {

    @Test
    void registrationIsDisabledByDefaultInApplicationConfiguration() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(RegistrationFlagConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(RegistrationFlag.class).enabled).isFalse();
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class RegistrationFlagConfiguration {
        @Bean
        RegistrationFlag registrationFlag(
                @Value("${app.registration.enabled:false}") boolean enabled) {
            return new RegistrationFlag(enabled);
        }
    }

    static class RegistrationFlag {
        private final boolean enabled;

        RegistrationFlag(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
