package io.pivotal.config;

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.cloud.config.client.ConfigClientProperties;
import org.springframework.cloud.config.client.ConfigServicePropertySourceLocator;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.HashMap;
import java.util.Map;

import static io.pivotal.config.LocalConfigFileEnvironmentProcessor.DEFAULT_PROPERTIES;

public class LocalConfigFileEnvironmentProcessorTest {

    private final ConfigurableEnvironment environment = new StandardEnvironment();

    private final ConfigClientProperties defaults = new ConfigClientProperties(environment);

    private final ConfigServicePropertySourceLocator locator = new ConfigServicePropertySourceLocator(defaults);

    @Test
    public void testProcessEnvironment() throws Exception {
        Map<String, Object> defaultProperties = new HashMap<>();
        defaultProperties.put("spring.application.name", "hello-tomcat");
        String[] profiles = {"default"};
        this.environment.getPropertySources().addFirst(new MapPropertySource(DEFAULT_PROPERTIES, defaultProperties));
        if (!StringUtils.isAnyBlank(profiles)) {
            for (String profile : profiles) {
                this.environment.addActiveProfile(profile);
            }
        }
        PropertySource source = this.locator.locate(environment);
        this.environment.getPropertySources().addFirst(source);

        LocalConfigFileEnvironmentProcessor localConfigFileEnvironmentProcessor = new LocalConfigFileEnvironmentProcessor();
        localConfigFileEnvironmentProcessor.processEnvironment(environment, source);
        Assert.assertNotNull(source);
        Assert.assertEquals("baz", source.getProperty("foo"));
        Assert.assertEquals("test", source.getProperty("testprop"));
    }

}