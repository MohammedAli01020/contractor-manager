package com.contractor.app.platform.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

class PlatformOwnerBootstrapCliTest {

    @Test
    void bootstrapCliRunsWithoutStartingAWebServer() {
        SpringApplication application = PlatformOwnerBootstrapCli.createApplication();

        assertEquals(WebApplicationType.NONE, application.getWebApplicationType());
        assertEquals("true", application.getDefaultProperties().get("app.platform.bootstrap.enabled").toString());
    }
}
