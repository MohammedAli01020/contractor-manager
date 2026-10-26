package com.contractor.app.platform.infra;

import com.contractor.app.ContractorApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * One-time command-line entry point for creating the initial Platform Owner.
 *
 * <p>Provide PLATFORM_OWNER_USERNAME, PLATFORM_OWNER_FULL_NAME, PLATFORM_OWNER_PASSWORD,
 * DB_USERNAME, DB_PASSWORD, and the application's required secret environment variables
 * before running this class. The bootstrap password is read from the environment by
 * PlatformOwnerBootstrapRunner; it is never accepted as a command-line argument.</p>
 */
public final class PlatformOwnerBootstrapCli {

    private PlatformOwnerBootstrapCli() {
    }

    static SpringApplication createApplication() {
        return new SpringApplicationBuilder(ContractorApplication.class)
                .web(WebApplicationType.NONE)
                .properties("app.platform.bootstrap.enabled=true")
                .build();
    }

    static void verifyBootstrapRunnerStarted(ConfigurableApplicationContext context) {
        if (context.getBeansOfType(PlatformOwnerBootstrapRunner.class).isEmpty()) {
            throw new IllegalStateException(
                    "Platform Owner bootstrap runner did not start; refusing to report success.");
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext context = null;
        int exitCode = 0;

        try {
            context = createApplication().run(args);
            verifyBootstrapRunnerStarted(context);
            System.out.println("Initial Platform Owner bootstrap completed.");
        } catch (Exception exception) {
            System.err.println(
                    "Platform Owner bootstrap failed. Check the required environment variables, "
                            + "database connectivity, and application logs.");
            exitCode = 1;
        } finally {
            if (context != null) {
                context.close();
            }
        }

        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}
