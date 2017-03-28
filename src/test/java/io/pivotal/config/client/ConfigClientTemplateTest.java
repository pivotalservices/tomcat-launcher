package io.pivotal.config.client;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.PropertySource;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * Some of these are integration tests... Need to run config server on port 8888
 * and point it to github.com/malston/config-repo. TODO: Move integration tests
 * into separate folder.
 *
 * @author malston
 */
public class ConfigClientTemplateTest {

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Test
    public void testCoolDb() throws Exception {
        ConfigClientTemplate<?> configClientTemplate = new ConfigClientTemplate<Object>("http://localhost:8888", "foo",
                new String[] { "db" });
        assertEquals("mycooldb", configClientTemplate.getProperty("foo.db"));
    }

    @Test
    public void testOverrideSpringProfilesActive() throws Exception {
        environmentVariables.set("SPRING_PROFILES_ACTIVE", "foo,db");
        ConfigClientTemplate configClientTemplate = new ConfigClientTemplate("http://localhost:8888", "foo",null);
        assertEquals("mycooldb", configClientTemplate.getProperty("foo.db"));
    }

    @Test
    public void testConfigPrecedenceOrder() throws Exception {
        ConfigClientTemplate<?> configClientTemplate = new ConfigClientTemplate<CompositePropertySource>("http://localhost:8888", "foo",
                new String[] { "development, db" });
        CompositePropertySource source = (CompositePropertySource) configClientTemplate.getPropertySource();
        assertThat("property sources", source.getPropertySources().size(), equalTo(10));
        assertThat(source.getPropertySources().stream()
                        .map(PropertySource::getName)
                        .collect(toList()),
                contains("configClient",
                        "https://github.com/malston/config-repo/foo-db.properties",
                        "https://github.com/malston/config-repo/foo-development.properties",
                        "https://github.com/malston/config-repo/foo.properties",
                        "https://github.com/malston/config-repo/application.yml",
                        "systemProperties",
                        "systemEnvironment",
                        "random",
                        "applicationConfig: [profile=]",
                        "defaultProperties"));
    }

    @Test
    public void testDefaultProperties() throws Exception {
        ConfigClientTemplate<?> configClientTemplate = new ConfigClientTemplate<CompositePropertySource>("http://localhost:8888", "foo",
                new String[] { "default" });
        Assert.assertNotNull(configClientTemplate.getPropertySource());
        Assert.assertEquals("from foo props", configClientTemplate.getPropertySource().getProperty("foo"));
        Assert.assertEquals("test", configClientTemplate.getPropertySource().getProperty("testprop"));
    }

}
