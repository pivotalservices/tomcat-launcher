package io.pivotal.launch;

import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.ContextEnvironment;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.PropertySource;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * Some of these are integration tests... Need to run config server on port 8888
 * and point it to github.com/malston/config-repo. TODO: Move integration tests
 * into separate folder.
 *
 * @author malston
 */
public class TomcatConfigurerTest {

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Test
    public void testLoadConfiguration() throws Exception {
        TomcatConfigurer tomcatConfigurer = new TomcatConfigurer("http://localhost:8888", "application",
                new String[] { "default" });
        tomcatConfigurer.setConfigurationLoader(() -> new PropertySource.StubPropertySource("test"));
        PropertySource<?> source = tomcatConfigurer.loadConfiguration();
        assertThat(source, instanceOf(PropertySource.StubPropertySource.class));
        assertEquals("test", source.getName());
    }

    @Test
    public void testCoolDb() throws Exception {
        TomcatConfigurer tomcatConfigurer = new TomcatConfigurer("http://localhost:8888", "foo",
                new String[] { "db" });
        CompositePropertySource source = (CompositePropertySource) tomcatConfigurer.loadConfiguration();
        assertNotNull(source);
        assertEquals("mycooldb", source.getProperty("foo.db"));
    }

    @Test
    public void testConfigPrecedenceOrder() throws Exception {
        TomcatConfigurer tomcatConfigurer = new TomcatConfigurer("http://localhost:8888", "foo",
                new String[] { "development, db" });
        CompositePropertySource source = (CompositePropertySource) tomcatConfigurer.loadConfiguration();
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
    public void testCreateStandardContext() throws Exception {
        TomcatConfigurer tomcatConfigurer = new TomcatConfigurer("http://localhost:8888", "application",
                new String[] { "default" });
        Tomcat tomcat = new Tomcat();
        StandardContext ctx = tomcatConfigurer.createStandardContext(tomcat);
        assertNotNull(ctx);
    }

    @Test
    public void testLoadLocalConfigurationFromConfigServer() throws Exception {
        TomcatConfigurer tomcatConfigurer = new TomcatConfigurer("http://localhost:8888", "application",
                new String[] { "default" });
        PropertySource<?> source = tomcatConfigurer.loadConfiguration();
        assertNotNull(source);
        ContextEnvironment ctxEnv = tomcatConfigurer.getEnvironment(source, "foo");
        assertEquals(ctxEnv.getValue(), "baz");
    }

    @Test
    public void testLoadLocalConfigurationFromFile() throws Exception {
        TomcatConfigurer tomcatConfigurer = new TomcatConfigurer("http://bogus", "application",
                new String[] { "default" });
        PropertySource<?> source = tomcatConfigurer.loadConfiguration();
        assertNotNull(source);
        ContextEnvironment ctxEnv = tomcatConfigurer.getEnvironment(source, "foo");
        assertEquals(ctxEnv.getValue(), "mark_laptop");
    }

    @Test
    public void testLoadEnvironmentVariableFromConfigServer() throws Exception {
        TomcatConfigurer tomcatConfigurer = new TomcatConfigurer("http://localhost:8888", "application",
                new String[] { "default" });
        environmentVariables.set("CONFIG_TEST", "foobar");
        assertEquals("foobar", System.getenv("CONFIG_TEST"));
        PropertySource<?> source = tomcatConfigurer.loadConfiguration();
        assertNotNull(source);
        ContextEnvironment ctxEnv = tomcatConfigurer.getEnvironment(source, "CONFIG_TEST");
        assertEquals(ctxEnv.getValue(), "foobar");
    }

}