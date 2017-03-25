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

	private final RestTemplate restTemplate = new RestTemplate();

	private final ConfigFileEnvironmentProcessor configFileEnvironmentProcessor;

	public ConfigClientTemplate(final String configServerUrl, final String app, final String[] profiles) {
		Assert.hasLength(configServerUrl, "You MUST set the config server URI");
		if (!configServerUrl.startsWith(HTTP_SCHEME) && !configServerUrl.startsWith(HTTPS_SCHEME)) {
			throw new RuntimeException("You MUST put the URI scheme in front of the config server URI");
		}
		Map<String, Object> defaultProperties = new HashMap<>();
		defaultProperties.put("spring.application.name", app);
		this.environment.getPropertySources()
				.addLast(new MapPropertySource(ConfigFileEnvironmentProcessor.DEFAULT_PROPERTIES, defaultProperties));
		for (String profile : profiles) {
			this.environment.addActiveProfile(profile);
		}
		this.defaults = new ConfigClientProperties(environment);
		this.defaults.setFailFast(false);
		this.defaults.setUri(configServerUrl);
		DefaultUriTemplateHandler uriTemplateHandler = new DefaultUriTemplateHandler();
		uriTemplateHandler.setBaseUrl(configServerUrl);
		this.restTemplate.setUriTemplateHandler(uriTemplateHandler);
		ConfigServicePropertySourceLocator locator = new ConfigServicePropertySourceLocator(defaults);
		this.configFileEnvironmentProcessor = new ConfigFileEnvironmentProcessor(environment, locator);
	}

	@SuppressWarnings("unchecked")
	public T getProperty(String name) {
		return (T) configFileEnvironmentProcessor.getPropertySource().getProperty(name);
	}

	public PropertySource<?> getPropertySource() {
		return configFileEnvironmentProcessor.getPropertySource();
	}

	static final class ConfigFileEnvironmentProcessor extends ConfigFileApplicationListener
			implements PropertySourceProvider {

		static final String DEFAULT_PROPERTIES = "defaultProperties";

		/**
		 * Name of the application configuration {@link PropertySource}.
		 */
		static final String APPLICATION_CONFIGURATION_PROPERTY_SOURCE_NAME = ConfigFileApplicationListener.APPLICATION_CONFIGURATION_PROPERTY_SOURCE_NAME;

		private ResourceLoader resourceLoader;

		private final ConfigurableEnvironment environment;
		
		private final ConfigServicePropertySourceLocator locator;
		
		private PropertySource<?> source;
		
		public ConfigFileEnvironmentProcessor(ConfigurableEnvironment environment, ConfigServicePropertySourceLocator locator) {
			this.environment = environment;
			this.locator = locator;
			this.resourceLoader = new DefaultResourceLoader(this.getClassLoader());
		}

		public PropertySource<?> getPropertySource() {
			if (source != null) {
				return source;
			}
			source = getPropertySource(this.locator);

			return source == null ? this.environment.getPropertySources().get(APPLICATION_CONFIGURATION_PROPERTY_SOURCE_NAME) : source;
		}
		
		public void refresh() {
			source = getPropertySource(this.locator);
		}

		public ClassLoader getClassLoader() {
			return this.resourceLoader != null ? this.resourceLoader.getClassLoader()
					: ClassUtils.getDefaultClassLoader();
		}

		public ResourceLoader getResourceLoader() {
			return this.resourceLoader;
		}

		private PropertySource<?> getPropertySource(ConfigServicePropertySourceLocator locator) {
			PropertySource<?> source = locator.locate(this.environment);
			if (source != null) {
				this.environment.getPropertySources().addFirst(source);
			}

			addPropertySources(environment, this.getResourceLoader());
			addPropertySources((CompositePropertySource) source);

			return source;
		}

		private void addPropertySources(CompositePropertySource composite) {
			if (environment != null && composite != null) {
				if (environment.getPropertySources() != null) {
					for (PropertySource source : environment.getPropertySources()) {
						if (source.getSource() instanceof Map) {
							@SuppressWarnings("unchecked")
							Map<String, Object> map = (Map<String, Object>) source.getSource();
							composite.addPropertySource(new MapPropertySource(source.getName(), map));
						} else if (source.getSource() instanceof List) {
							List sources = (List) source.getSource();
							for (Object src : sources) {
								if (src instanceof EnumerablePropertySource) {
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
	}

}
