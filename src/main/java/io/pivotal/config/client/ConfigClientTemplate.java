/**
 * 
 */
package io.pivotal.config.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.config.ConfigFileApplicationListener;
import org.springframework.cloud.config.client.ConfigClientProperties;
import org.springframework.cloud.config.client.ConfigServicePropertySourceLocator;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriTemplateHandler;

/**
 * @author malston
 *
 */
public class ConfigClientTemplate<T> implements PropertySourceProvider {

    private static final String HTTPS_SCHEME = "https://";

    private static final String HTTP_SCHEME = "http://";

    private final ConfigurableEnvironment environment = new StandardEnvironment();

    private final ConfigClientProperties defaults;

    private final ConfigServicePropertySourceLocator locator;

    private final RestTemplate restTemplate = new RestTemplate();

    private final LocalConfigFileEnvironmentProcessor localConfigFileEnvironmentProcessor = new LocalConfigFileEnvironmentProcessor();
    
    private final PropertySource<?> source;

    public ConfigClientTemplate(final String configServerUrl, final String app, final String[] profiles) {
        Assert.hasLength(configServerUrl, "You MUST set the config server URI");
        if (!configServerUrl.startsWith(HTTP_SCHEME) && !configServerUrl.startsWith(HTTPS_SCHEME)) {
            throw new RuntimeException("You MUST put the URI scheme in front of the config server URI");
        }
        Map<String, Object> defaultProperties = new HashMap<>();
        defaultProperties.put("spring.application.name", app);
        this.environment.getPropertySources().addLast(new MapPropertySource(LocalConfigFileEnvironmentProcessor.DEFAULT_PROPERTIES, defaultProperties));
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

        return source == null ? this.environment.getPropertySources().get(LocalConfigFileEnvironmentProcessor.APPLICATION_CONFIGURATION_PROPERTY_SOURCE_NAME) : source;
    }
    
    @SuppressWarnings("unchecked")
	public T getProperty(String name) {
    	return (T) this.source.getProperty(name);
    }

	public PropertySource<?> getPropertySource() {
		return this.source;
	}
	
	static final class LocalConfigFileEnvironmentProcessor extends ConfigFileApplicationListener {

	    public static final String DEFAULT_PROPERTIES = "defaultProperties";

	    /**
	     * Name of the application configuration {@link PropertySource}.
	     */
	    public static final String APPLICATION_CONFIGURATION_PROPERTY_SOURCE_NAME = "applicationConfigurationProperties";

	    private ResourceLoader resourceLoader;

	    public LocalConfigFileEnvironmentProcessor() {
	        this.resourceLoader = new DefaultResourceLoader(this.getClassLoader());
	    }

	    public void processEnvironment(ConfigurableEnvironment environment, PropertySource source) {
	        addPropertySources(environment, this.getResourceLoader());
	        merge(environment, (CompositePropertySource)source);
	    }

	    private void merge(ConfigurableEnvironment environment, CompositePropertySource composite) {
	        if (environment != null && composite != null) {
	            if (environment.getPropertySources() != null) {
	                for (PropertySource source : environment.getPropertySources()) {
	                    if (source.getSource() instanceof Map) {
	                        @SuppressWarnings("unchecked")
	                        Map<String, Object> map = (Map<String, Object>) source
	                                .getSource();
	                        composite.addPropertySource(new MapPropertySource(source
	                                .getName(), map));
	                    } else if (source.getSource() instanceof List) {
	                        List sources = (List) source.getSource();
	                        for (Object src : sources) {
	                            if (src instanceof  EnumerablePropertySource) {
	                                EnumerablePropertySource enumerable = (EnumerablePropertySource) src;
	                                composite.addPropertySource(enumerable);
	                            }
	                        }
	                    } else if (!(source instanceof CompositePropertySource)) {
	                        composite.addPropertySource(source);
	                    }
	                }
	            }
	        }
	    }

	    public ClassLoader getClassLoader() {
	        return this.resourceLoader != null ? this.resourceLoader.getClassLoader() : ClassUtils.getDefaultClassLoader();
	    }

	    public ResourceLoader getResourceLoader() {
	        return this.resourceLoader;
	    }
	}

}
