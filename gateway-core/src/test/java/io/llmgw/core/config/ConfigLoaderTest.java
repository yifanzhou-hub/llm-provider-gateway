package io.llmgw.core.config;

import io.llmgw.spi.ErrorCode;
import io.llmgw.spi.GatewayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class ConfigLoaderTest {

    @TempDir
    Path tempDir;

    private String previousAppHome;

    @BeforeEach
    void pointAppHomeAtTempDir() {
        previousAppHome = System.getProperty("app.home");
        System.setProperty("app.home", tempDir.toString());
    }

    @AfterEach
    void restoreAppHome() {
        if (previousAppHome == null) {
            System.clearProperty("app.home");
        } else {
            System.setProperty("app.home", previousAppHome);
        }
    }

    private Path writeConfig(String yaml) throws IOException {
        Path file = tempDir.resolve("gateway.yml");
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
        return file;
    }

    private static final String MINIMAL = """
            defaultProvider: mock
            providers:
              mock:
                enabled: true
            """;

    @Test
    void minimalConfig_fillsDefaults() throws Exception {
        GatewayConfig config = ConfigLoader.load(writeConfig(MINIMAL));

        assertThat(config.version()).isEqualTo(1);
        assertThat(config.defaultProvider()).isEqualTo("mock");
        assertThat(config.requestTimeoutMs()).isEqualTo(3000);
        assertThat(config.maxRetries()).isZero();
        assertThat(config.pluginsDir()).isEqualTo(tempDir.resolve("plugins"));
        assertThat(config.providers()).containsOnlyKeys("mock");
        assertThat(config.providers().get("mock").enabled()).isTrue();
        assertThat(config.providers().get("mock").options()).isEmpty();
    }

    @Test
    void explicitValues_areKept() throws Exception {
        GatewayConfig config = ConfigLoader.load(writeConfig("""
                version: 1
                defaultProvider: mock
                requestTimeoutMs: 1500
                maxRetries: 2
                providers:
                  mock:
                    enabled: true
                """));

        assertThat(config.requestTimeoutMs()).isEqualTo(1500);
        assertThat(config.maxRetries()).isEqualTo(2);
    }

    @Test
    void missingFile_reportsAbsolutePath() {
        Path missing = tempDir.resolve("nope.yml");

        GatewayException e = catchThrowableOfType(() -> ConfigLoader.load(missing), GatewayException.class);

        assertThat(e.code()).isEqualTo(ErrorCode.CONFIG_INVALID);
        assertThat(e.retriable()).isFalse();
        assertThat(e.getMessage()).contains(missing.toAbsolutePath().toString());
    }

    @Test
    void brokenYaml_reportsLineNumber() throws Exception {
        Path file = writeConfig("""
                defaultProvider: [unclosed
                providers:
                  mock: {}
                """);

        GatewayException e = catchThrowableOfType(() -> ConfigLoader.load(file), GatewayException.class);

        assertThat(e.code()).isEqualTo(ErrorCode.CONFIG_INVALID);
        assertThat(e.getMessage()).contains("line");
    }

    @Test
    void missingDefaultProvider_namesTheField() throws Exception {
        Path file = writeConfig("""
                providers:
                  mock:
                    enabled: true
                """);

        GatewayException e = catchThrowableOfType(() -> ConfigLoader.load(file), GatewayException.class);

        assertThat(e.getMessage()).contains("defaultProvider");
    }

    @Test
    void defaultProviderNotListed_isRejected() throws Exception {
        Path file = writeConfig("""
                defaultProvider: ghost
                providers:
                  mock:
                    enabled: true
                """);

        GatewayException e = catchThrowableOfType(() -> ConfigLoader.load(file), GatewayException.class);

        assertThat(e.getMessage()).contains("ghost").contains("not listed");
    }

    @Test
    void disabledDefaultProvider_isRejected() throws Exception {
        Path file = writeConfig("""
                defaultProvider: mock
                providers:
                  mock:
                    enabled: false
                """);

        GatewayException e = catchThrowableOfType(() -> ConfigLoader.load(file), GatewayException.class);

        assertThat(e.getMessage()).contains("mock").contains("disabled");
    }

    @Test
    void nonPositiveTimeout_isRejected() throws Exception {
        assertThatThrownBy(() -> ConfigLoader.load(writeConfig("""
                defaultProvider: mock
                requestTimeoutMs: 0
                providers:
                  mock: {}
                """)))
                .isInstanceOf(GatewayException.class)
                .hasMessageContaining("requestTimeoutMs");
    }

    @Test
    void negativeMaxRetries_isRejected() throws Exception {
        assertThatThrownBy(() -> ConfigLoader.load(writeConfig("""
                defaultProvider: mock
                maxRetries: -1
                providers:
                  mock: {}
                """)))
                .isInstanceOf(GatewayException.class)
                .hasMessageContaining("maxRetries");
    }

    @Test
    void unknownTopLevelKey_isIgnored() throws Exception {
        GatewayConfig config = ConfigLoader.load(writeConfig(MINIMAL + "logLevel: debug\n"));

        assertThat(config.defaultProvider()).isEqualTo("mock");
        assertThat(config.providers()).containsOnlyKeys("mock");
    }

    @Test
    void pluginsDir_isResolvedAgainstAppHome() throws Exception {
        GatewayConfig config = ConfigLoader.load(writeConfig(MINIMAL + "pluginsDir: my-plugins\n"));

        assertThat(config.pluginsDir()).isAbsolute();
        assertThat(config.pluginsDir()).isEqualTo(tempDir.resolve("my-plugins"));
    }

    @Test
    void providerOptions_areKeptAsStrings() throws Exception {
        GatewayConfig config = ConfigLoader.load(writeConfig("""
                defaultProvider: mock
                providers:
                  mock:
                    enabled: true
                    delayMs: 0
                    failMode: none
                  completion:
                    enabled: false
                    endpoint: http://127.0.0.1:8099/v1/completions
                """));

        assertThat(config.providers().get("mock").options())
                .containsEntry("delayMs", "0")
                .containsEntry("failMode", "none");
        assertThat(config.providers().get("completion").enabled()).isFalse();
        assertThat(config.providers().get("completion").options())
                .containsEntry("endpoint", "http://127.0.0.1:8099/v1/completions");
    }

    @Test
    void nonBooleanEnabled_isRejected() throws Exception {
        assertThatThrownBy(() -> ConfigLoader.load(writeConfig("""
                defaultProvider: mock
                providers:
                  mock:
                    enabled: 1
                """)))
                .isInstanceOf(GatewayException.class)
                .hasMessageContaining("boolean");
    }

    @Test
    void rootThatIsNotAMapping_isRejected() throws Exception {
        assertThatThrownBy(() -> ConfigLoader.load(writeConfig("- just\n- a\n- list\n")))
                .isInstanceOf(GatewayException.class)
                .hasMessageContaining("mapping");
    }
}
