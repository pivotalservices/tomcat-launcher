package io.pivotal.launch;

import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.ContextEnvironment;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.springframework.core.env.PropertySource;

import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class TomcatConfigurerTest {

    @Rule
    public final EnvironmentVariables environmentVariables
            = new EnvironmentVariables();

    @Test(expected = RuntimeException.class)
    public void testRunWithNoConfigServerUrl() throws Exception {
        TomcatConfigurer tomcatConfigurer = new TomcatConfigurer();
        tomcatConfigurer.loadConfiguration("");
    }

    @Test
    public void testLoadConfiguration() throws Exception {
        ConfigurationLoader loader = () -> new PropertySource.StubPropertySource("test");
        TomcatConfigurer tomcatConfigurer = new TomcatConfigurer(loader);
        PropertySource source = tomcatConfigurer.loadConfiguration(null);
        assertThat(source, instanceOf(PropertySource.StubPropertySource.class));
    }

    @Test
    public void testCreateStandardContext() throws Exception {
        TomcatConfigurer tomcatConfigurer = new TomcatConfigurer();
        Tomcat tomcat = new Tomcat();
        StandardContext ctx = tomcatConfigurer.createStandardContext(tomcat);
        Assert.assertNotNull(ctx);
    }

    @Test
    public void testLoadLocalConfigurationFromConfigServer() throws Exception {
        TomcatConfigurer tomcatConfigurer = new TomcatConfigurer();
        PropertySource source = tomcatConfigurer.loadConfiguration("http://localhost:8888");
        Assert.assertNotNull(source);
        ContextEnvironment ctxEnv = tomcatConfigurer.getEnvironment(source, "foo");
        assertEquals(ctxEnv.getValue(), "baz");
    }

    @Test
    public void testLoadLocalConfigurationFromFile() throws Exception {
        TomcatConfigurer tomcatConfigurer = new TomcatConfigurer();
        PropertySource source = tomcatConfigurer.loadConfiguration("http://bogus");
        Assert.assertNotNull(source);
        ContextEnvironment ctxEnv = tomcatConfigurer.getEnvironment(source, "foo");
        assertEquals(ctxEnv.getValue(), "mark_laptop");
    }

    @Test
    public void testLoadEnvironmentVariableFromConfigServer() throws Exception {
        TomcatConfigurer tomcatConfigurer = new TomcatConfigurer();
        environmentVariables.set("CONFIG_TEST", "foobar");
        assertEquals("foobar", System.getenv("CONFIG_TEST"));
        PropertySource source = tomcatConfigurer.loadConfiguration("http://localhost:8888");
        Assert.assertNotNull(source);
        ContextEnvironment ctxEnv = tomcatConfigurer.getEnvironment(source, "CONFIG_TEST");
        assertEquals(ctxEnv.getValue(), "foobar");
    }

}