package io.pivotal.config.client;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.PropertySource;

public class ConfigClientTemplateTest {

    @Test
    public void testCoolDb() throws Exception {
        ConfigClientTemplate<?> configClientTemplate = new ConfigClientTemplate<Object>("http://localhost:8888", "foo",
                new String[] { "db" });
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
