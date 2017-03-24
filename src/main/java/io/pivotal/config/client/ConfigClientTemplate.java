/**
 * 
 */
package io.pivotal.config.client;

import static io.pivotal.config.LocalConfigFileEnvironmentProcessor.APPLICATION_CONFIGURATION_PROPERTY_SOURCE_NAME;
import static io.pivotal.config.LocalConfigFileEnvironmentProcessor.DEFAULT_PROPERTIES;

import java.util.HashMap;
import java.util.Map;

import org.springframework.cloud.config.client.ConfigClientProperties;
import org.springframework.cloud.config.client.ConfigServicePropertySourceLocator;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.Assert;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriTemplateHandler;

import io.pivotal.config.LocalConfigFileEnvironmentProcessor;

/**
 * @author malston
 *
 */
public class ConfigClientTemplate<T> implements ConfigClientOperations<T> {

    private static final String HTTPS_SCHEME = "https://";

    private static final String HTTP_SCHEME = "http://";

    private final ConfigurableEnvironment environment = new StandardEnvironment();

    private ConfigClientProperties defaults;

    private ConfigServicePropertySourceLocator locator;

    private final RestTemplate restTemplate = new RestTemplate();

    private final LocalConfigFileEnvironmentProcessor localConfigFileEnvironmentProcessor = new LocalConfigFileEnvironmentProcessor();
    
    private PropertySource<?> source = null;

    public ConfigClientTemplate(final String configServerUrl, final String app, final String[] profiles) {
        Assert.hasLength(configServerUrl, "You MUST set the config server URI");
        if (!configServerUrl.startsWith(HTTP_SCHEME) && !configServerUrl.startsWith(HTTPS_SCHEME)) {
            throw new RuntimeException("You MUST put the URI scheme in front of the config server URI");
        }
        Map<String, Object> defaultProperties = new HashMap<>();
        defaultProperties.put("spring.application.name", app);
        this.environment.getPropertySources().addLast(new MapPropertySource(DEFAULT_PROPERTIES, defaultProperties));
        for (String profile : profiles) {
            this.environment.addActiveProfile(profile);
        }
        this.defaults = new ConfigClientProperties(environment);
        this.defaults.setFailFast(false);
        this.defaults.setUri(configServerUrl);
        this.locator = new ConfigServicePropertySourceLocator(defaults);
        DefaultUriTemplateHandler uriTemplateHandler = new DefaultUriTemplateHandler();
        uriTemplateHandler.setBaseUrl(configServerUrl);
        this.restTemplate.setUriTemplateHandler(uriTemplateHandler);
        
        this.source = this.load();
    }

    private PropertySource<?> load() {
        PropertySource<?> source = this.locator.locate(this.environment);
        if (source != null) {
            this.environment.getPropertySources().addFirst(source);
        }
        this.localConfigFileEnvironmentProcessor.processEnvironment(environment, source);

        return source == null ? this.environment.getPropertySources().get(APPLICATION_CONFIGURATION_PROPERTY_SOURCE_NAME) : source;
    }
    
    @SuppressWarnings("unchecked")
	public T getProperty(String name) {
    	return (T) this.source.getProperty(name);
    }

	public PropertySource<?> getPropertySource() {
		return this.source;
	}

}
