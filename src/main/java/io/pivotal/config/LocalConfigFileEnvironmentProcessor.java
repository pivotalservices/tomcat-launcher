package io.pivotal.config;

import org.springframework.boot.context.config.ConfigFileApplicationListener;
import org.springframework.core.env.*;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.ClassUtils;

import java.util.List;
import java.util.Map;

/**
 * @see org.springframework.boot.context.config.ConfigFileApplicationListener
 */
public class LocalConfigFileEnvironmentProcessor extends ConfigFileApplicationListener {

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
