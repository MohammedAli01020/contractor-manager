package com.contractor.app.platform.infra;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

class PlatformOwnerBootstrapCliTest {

    @Test
    void bootstrapCliRunsWithoutStartingAWebServer() {
        SpringApplication application = PlatformOwnerBootstrapCli.createApplication();

        assertEquals(WebApplicationType.NONE, application.getWebApplicationType());
    }

    @Test
    void refusesToReportSuccessWhenBootstrapRunnerIsAbsent() {
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        when(context.getBeansOfType(PlatformOwnerBootstrapRunner.class))
                .thenReturn(Collections.emptyMap());

        assertThrows(IllegalStateException.class,
                () -> PlatformOwnerBootstrapCli.verifyBootstrapRunnerStarted(context));
    }

    @Test
    void acceptsSuccessfulStartupWhenBootstrapRunnerIsPresent() {
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        PlatformOwnerBootstrapRunner runner = mock(PlatformOwnerBootstrapRunner.class);
        when(context.getBeansOfType(PlatformOwnerBootstrapRunner.class))
                .thenReturn(Map.of("platformOwnerBootstrapRunner", runner));

        assertDoesNotThrow(
                () -> PlatformOwnerBootstrapCli.verifyBootstrapRunnerStarted(context));
    }
}
