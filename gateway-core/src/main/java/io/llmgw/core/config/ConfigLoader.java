package io.llmgw.core.config;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.llmgw.spi.ErrorCode;
import io.llmgw.spi.GatewayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Reads {@code gateway.yml} and turns it into a validated {@link GatewayConfig}.
 *
 * <p>The loader only checks what it can see in the file: types, ranges and the presence of an
 * enabled {@code defaultProvider}. Whether that provider actually exists is decided later, when
 * the registry has been assembled from the plugin directory.
 */
public final class ConfigLoader {

    /** Directory scanned for provider jars when the config does not say otherwise. */
    public static final String DEFAULT_PLUGINS_DIR = "plugins";

    /** Default per-call budget when the config does not say otherwise. */
    public static final int DEFAULT_TIMEOUT_MS = 3000;

    /** Schema version this build understands. */
    public static final int SUPPORTED_VERSION = 1;

    private static final Logger LOG = LoggerFactory.getLogger(ConfigLoader.class);
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final Set<String> KNOWN_KEYS = Set.of(
            "version", "pluginsDir", "defaultProvider", "requestTimeoutMs", "maxRetries", "providers");

    private ConfigLoader() {
    }

    /**
     * Loads and validates a config file.
     *
     * @param configFile path to {@code gateway.yml}; resolved against the working directory
     * @return validated configuration
     * @throws GatewayException with {@link ErrorCode#CONFIG_INVALID} on any problem
     */
    public static GatewayConfig load(Path configFile) throws GatewayException {
        Path absolute = resolveConfigPath(configFile);
        JsonNode root = readYaml(absolute);
        if (root == null || !root.isObject()) {
            throw invalid("config root must be a mapping: " + absolute);
        }
        warnUnknownKeys(root);

        int version = optionalInt(root, "version", SUPPORTED_VERSION);
        if (version != SUPPORTED_VERSION) {
            LOG.warn("config version {} is not supported version {}, continuing anyway",
                    version, SUPPORTED_VERSION);
        }

        int timeoutMs = optionalInt(root, "requestTimeoutMs", DEFAULT_TIMEOUT_MS);
        if (timeoutMs <= 0) {
            throw invalid("requestTimeoutMs must be > 0, got " + timeoutMs);
        }

        int maxRetries = optionalInt(root, "maxRetries", 0);
        if (maxRetries < 0) {
            throw invalid("maxRetries must be >= 0, got " + maxRetries);
        }

        String defaultProvider = requiredText(root, "defaultProvider");
        Map<String, GatewayConfig.ProviderSettings> providers = readProviders(root.get("providers"));

        GatewayConfig.ProviderSettings settings = providers.get(defaultProvider);
        if (settings == null) {
            throw invalid("defaultProvider '" + defaultProvider + "' is not listed under providers");
        }
        if (!settings.enabled()) {
            throw invalid("defaultProvider '" + defaultProvider + "' is disabled");
        }

        Path pluginsDir = resolvePluginsDir(root.get("pluginsDir"));
        LOG.debug("config loaded from {}: defaultProvider={}, timeoutMs={}, pluginsDir={}",
                absolute, defaultProvider, timeoutMs, pluginsDir);
        return new GatewayConfig(version, pluginsDir, defaultProvider, timeoutMs, maxRetries, providers);
    }

    private static Path resolveConfigPath(Path configFile) throws GatewayException {
        if (configFile == null) {
            throw invalid("config file path is null");
        }
        Path absolute = configFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absolute)) {
            throw invalid("config file not found: " + absolute);
        }
        return absolute;
    }

    private static JsonNode readYaml(Path absolute) throws GatewayException {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(absolute);
        } catch (IOException e) {
            throw new GatewayException(ErrorCode.CONFIG_INVALID,
                    "cannot read config file " + absolute + ": " + e.getMessage(), false, null, e);
        }
        try {
            return YAML.readTree(bytes);
        } catch (JsonProcessingException e) {
            throw invalid(yamlError(absolute, e), e);
        } catch (IOException e) {
            throw new GatewayException(ErrorCode.CONFIG_INVALID,
                    "cannot parse config file " + absolute + ": " + e.getMessage(), false, null, e);
        }
    }

    private static String yamlError(Path absolute, JsonProcessingException e) {
        JsonLocation location = e.getLocation();
        if (location == null) {
            return "YAML syntax error in " + absolute + ": " + e.getOriginalMessage();
        }
        return "YAML syntax error in " + absolute + " at line " + location.getLineNr()
                + ", column " + location.getColumnNr() + ": " + e.getOriginalMessage();
    }

    private static void warnUnknownKeys(JsonNode root) {
        Iterator<String> names = root.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!KNOWN_KEYS.contains(name)) {
                LOG.warn("unknown config key '{}' ignored", name);
            }
        }
    }

    private static int optionalInt(JsonNode root, String field, int defaultValue) throws GatewayException {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return defaultValue;
        }
        if (!node.isIntegralNumber() || !node.canConvertToInt()) {
            throw invalid("field '" + field + "' must be an integer, got: " + node);
        }
        return node.intValue();
    }

    private static String requiredText(JsonNode root, String field) throws GatewayException {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            throw invalid("missing required field: " + field);
        }
        if (!node.isTextual() || node.textValue().isBlank()) {
            throw invalid("field '" + field + "' must be a non-empty string");
        }
        return node.textValue();
    }

    private static Map<String, GatewayConfig.ProviderSettings> readProviders(JsonNode node)
            throws GatewayException {
        Map<String, GatewayConfig.ProviderSettings> providers = new LinkedHashMap<>();
        if (node == null || node.isNull()) {
            return providers;
        }
        if (!node.isObject()) {
            throw invalid("field 'providers' must be a mapping of provider id to settings");
        }
        Iterator<Map.Entry<String, JsonNode>> entries = node.fields();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            String id = entry.getKey();
            if (id.isBlank()) {
                throw invalid("provider id must not be blank");
            }
            providers.put(id, readProviderSettings(id, entry.getValue()));
        }
        return providers;
    }

    private static GatewayConfig.ProviderSettings readProviderSettings(String id, JsonNode node)
            throws GatewayException {
        if (node == null || node.isNull()) {
            return GatewayConfig.ProviderSettings.DEFAULT;
        }
        if (!node.isObject()) {
            throw invalid("providers." + id + " must be a mapping");
        }
        boolean enabled = true;
        Map<String, String> options = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = field.getKey();
            JsonNode value = field.getValue();
            if ("enabled".equals(key)) {
                if (!value.isBoolean()) {
                    throw invalid("providers." + id + ".enabled must be a boolean");
                }
                enabled = value.booleanValue();
            } else if (value != null && !value.isNull()) {
                if (value.isContainerNode()) {
                    throw invalid("providers." + id + "." + key + " must be a scalar");
                }
                options.put(key, value.asText());
            }
        }
        return new GatewayConfig.ProviderSettings(enabled, Map.copyOf(options));
    }

    private static Path resolvePluginsDir(JsonNode node) throws GatewayException {
        String raw = DEFAULT_PLUGINS_DIR;
        if (node != null && !node.isNull()) {
            if (!node.isTextual() || node.textValue().isBlank()) {
                throw invalid("field 'pluginsDir' must be a non-empty string");
            }
            raw = node.textValue();
        }
        Path dir = Path.of(raw);
        if (!dir.isAbsolute()) {
            dir = appHome().resolve(dir);
        }
        return dir.normalize();
    }

    /** Base directory for relative paths; {@code -Dapp.home} when the launcher sets it. */
    private static Path appHome() {
        String home = System.getProperty("app.home");
        if (home == null || home.isBlank()) {
            LOG.debug("system property app.home is not set, falling back to the working directory");
            return Path.of("").toAbsolutePath();
        }
        return Path.of(home).toAbsolutePath();
    }

    private static GatewayException invalid(String message) {
        return new GatewayException(ErrorCode.CONFIG_INVALID, message, false, null);
    }

    private static GatewayException invalid(String message, Throwable cause) {
        return new GatewayException(ErrorCode.CONFIG_INVALID, message, false, null, cause);
    }
}
