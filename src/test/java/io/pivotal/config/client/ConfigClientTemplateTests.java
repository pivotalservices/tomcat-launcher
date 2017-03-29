package io.pivotal.config.client;

import io.pivotal.config.server.TestConfigServer;
import org.junit.*;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.config.client.ConfigServicePropertySourceLocator;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.Executors;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.*;

/**
 * Some of these are integration tests... Need to run config server on port 8888
 * and point it to github.com/malston/config-repo. TODO: Move integration tests
 * into separate folder.
 *
 * @author malston
 */
@RunWith(SpringRunner.class)
// Explicitly enable config client because test classpath has config server on it
@SpringBootTest(properties = {"spring.cloud.config.enabled=true",
        "logging.level.org.springframework.retry=TRACE"},
        classes = StandaloneClientApplication.class)
@DirtiesContext
public class ConfigClientTemplateTests {

    @Autowired
    private ConfigServicePropertySourceLocator locator;

    private static ConfigurableApplicationContext context;

    @BeforeClass
    public static void delayConfigServer() {
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(2000L);
                } catch (InterruptedException e) {
                }
                context = TestConfigServer.start();
            }
        });
    }

    @AfterClass
    public static void shutdown() {
        if (context != null) {
            context.close();
        }
    }

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Test
    public void testCoolDb() throws Exception {
        ConfigClientTemplate<?> configClientTemplate = new ConfigClientTemplate<Object>("http://localhost:8888", "foo",
                new String[]{"db"});
        assertEquals("mycooldb", configClientTemplate.getProperty("foo.db"));
    }

    @Test
    public void testOverrideSpringProfilesActive() throws Exception {
        environmentVariables.set("SPRING_PROFILES_ACTIVE", "foo,db");
        ConfigClientTemplate configClientTemplate = new ConfigClientTemplate("http://localhost:8888", "foo", null);
        assertEquals("mycooldb", configClientTemplate.getProperty("foo.db"));
    }

    @Test
    public void testConfigPrecedenceOrder() throws Exception {
        ConfigClientTemplate<?> configClientTemplate = new ConfigClientTemplate<CompositePropertySource>("http://localhost:8888", "foo",
                new String[]{"development, db"});
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
                new String[]{"default"});
        Assert.assertNotNull(configClientTemplate.getPropertySource());
        Assert.assertEquals("from foo props", configClientTemplate.getPropertySource().getProperty("foo"));
        Assert.assertEquals("test", configClientTemplate.getPropertySource().getProperty("testprop"));
    }

    @Test
    public void testLoadLocalConfigurationFromConfigServer() throws Exception {
        ConfigClientTemplate<?> configClientTemplate = new ConfigClientTemplate("http://localhost:8888", "application",
                new String[]{"default"});
        PropertySource<?> source = configClientTemplate.getPropertySource();
        assertNotNull(source);
        String foo = (String) configClientTemplate.getPropertySource().getProperty("foo");
        assertEquals(foo, "baz");
    }

    @Test
    public void testLoadEnvironmentVariableFromConfigServer() throws Exception {
        ConfigClientTemplate<?> configClientTemplate = new ConfigClientTemplate("http://localhost:8888", "application",
                new String[]{"default"});
        environmentVariables.set("CONFIG_TEST", "foobar");
        assertEquals("foobar", System.getenv("CONFIG_TEST"));
        PropertySource<?> source = configClientTemplate.getPropertySource();
        assertNotNull(source);
        String test = (String) configClientTemplate.getPropertySource().getProperty("CONFIG_TEST");
        assertEquals(test, "foobar");
    }

}