package io.pivotal.config;

import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.config.client.ConfigClientProperties;
import org.springframework.cloud.config.client.ConfigServicePropertySourceLocator;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.Assert;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriTemplateHandler;

import java.util.HashMap;
import java.util.Map;

import static io.pivotal.config.LocalConfigFileEnvironmentProcessor.APPLICATION_CONFIGURATION_PROPERTY_SOURCE_NAME;
import static io.pivotal.config.LocalConfigFileEnvironmentProcessor.DEFAULT_PROPERTIES;

public class DefaultConfigurationLoader implements ConfigurationLoader {

    private static final String HTTPS_SCHEME = "https://";

    private static final String HTTP_SCHEME = "http://";

    private final ConfigurableEnvironment environment = new StandardEnvironment();

    private final ConfigClientProperties defaults = new ConfigClientProperties(environment);

    private final ConfigServicePropertySourceLocator locator = new ConfigServicePropertySourceLocator(defaults);

    private final RestTemplate restTemplate = new RestTemplate();

    private final LocalConfigFileEnvironmentProcessor localConfigFileEnvironmentProcessor = new LocalConfigFileEnvironmentProcessor();

    public DefaultConfigurationLoader(final String configServerUrl) {
        Assert.hasLength(configServerUrl, "You MUST set the config server URI");
        if (!configServerUrl.startsWith(HTTP_SCHEME) && !configServerUrl.startsWith(HTTPS_SCHEME)) {
            throw new RuntimeException("You MUST put the URI scheme in front of the config server URI");
        }
        this.defaults.setFailFast(false);
        this.defaults.setUri(configServerUrl);
        DefaultUriTemplateHandler uriTemplateHandler = new DefaultUriTemplateHandler();
        uriTemplateHandler.setBaseUrl(configServerUrl);
        this.restTemplate.setUriTemplateHandler(uriTemplateHandler);
        this.locator.setRestTemplate(restTemplate);
    }

    public PropertySource load(String appName, String[] profiles) {
        Map<String, Object> defaultProperties = new HashMap<>();
        if (StringUtils.isNotEmpty(appName)) {
            defaultProperties.put("spring.application.name", appName);
        }
        this.environment.getPropertySources().addFirst(new MapPropertySource(DEFAULT_PROPERTIES, defaultProperties));
        if (!StringUtils.isAnyBlank(profiles)) {
            for (String profile : profiles) {
                this.environment.addActiveProfile(profile);
            }
        }
        PropertySource source = this.locator.locate(this.environment);
        if (source != null) {
            this.environment.getPropertySources().addFirst(source);
        }
        this.localConfigFileEnvironmentProcessor.processEnvironment(environment, source);

        return source == null ? this.environment.getPropertySources().get(APPLICATION_CONFIGURATION_PROPERTY_SOURCE_NAME) : source;
    }
}
