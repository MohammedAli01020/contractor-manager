package com.contractor.app.platform.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

class PlatformOwnerBootstrapCliTest {

    @Test
    void bootstrapCliRunsWithoutStartingAWebServer() {
        SpringApplication application = PlatformOwnerBootstrapCli.createApplication();

        assertEquals(WebApplicationType.NONE, application.getWebApplicationType());
    }
}
