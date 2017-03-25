package io.pivotal.tomcat.launch;

import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.ContextEnvironment;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.PropertySource;

import io.pivotal.tomcat.launch.TomcatLaunchConfigurer;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.util.HashMap;

/**
 * Some of these are integration tests... Need to run config server on port 8888
 * and point it to github.com/malston/config-repo. TODO: Move integration tests
 * into separate folder.
 *
 * @author malston
 */
public class TomcatLaunchConfigurerTest {

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Test
    public void testLoadConfiguration() throws Exception {
    	TomcatLaunchConfigurer tomcatLaunchConfigurer = new TomcatLaunchConfigurer(() -> new PropertySource.StubPropertySource("test"));
        PropertySource<?> source = tomcatLaunchConfigurer.getPropertySource();
        assertThat(source, instanceOf(PropertySource.StubPropertySource.class));
        assertEquals("test", source.getName());
    }

    @Test
    public void testCoolDb() throws Exception {
        TomcatLaunchConfigurer tomcatLaunchConfigurer = new TomcatLaunchConfigurer("http://localhost:8888", "foo",
                new String[] { "db" });
        CompositePropertySource source = (CompositePropertySource) tomcatLaunchConfigurer.getPropertySource();
        assertNotNull(source);
        assertEquals("mycooldb", source.getProperty("foo.db"));
    }

    @Test
    public void testConfigPrecedenceOrder() throws Exception {
        TomcatLaunchConfigurer tomcatLaunchConfigurer = new TomcatLaunchConfigurer("http://localhost:8888", "foo",
                new String[] { "development, db" });
        CompositePropertySource source = (CompositePropertySource) tomcatLaunchConfigurer.getPropertySource();
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
    	TomcatLaunchConfigurer tomcatLaunchConfigurer = new TomcatLaunchConfigurer("http://localhost:8888", "application",
                new String[] { "default" });
        Tomcat tomcat = new Tomcat();
        StandardContext ctx = tomcatLaunchConfigurer.createStandardContext(tomcat);
        assertNotNull(ctx);
    }

    @Test
    public void testLoadLocalConfigurationFromConfigServer() throws Exception {
    	TomcatLaunchConfigurer tomcatLaunchConfigurer = new TomcatLaunchConfigurer("http://localhost:8888", "application",
                new String[] { "default" });
        PropertySource<?> source = tomcatLaunchConfigurer.getPropertySource();
        assertNotNull(source);
        ContextEnvironment ctxEnv = tomcatLaunchConfigurer.getEnvironment(source, "foo");
        assertEquals(ctxEnv.getValue(), "baz");
    }

    @Test
    public void testLoadLocalConfigurationFromFile() throws Exception {
    	TomcatLaunchConfigurer tomcatLaunchConfigurer = new TomcatLaunchConfigurer("http://bogus", "application",
                new String[] { "default" });
        PropertySource<?> source = tomcatLaunchConfigurer.getPropertySource();
        assertNotNull(source);
        ContextEnvironment ctxEnv = tomcatLaunchConfigurer.getEnvironment(source, "foo");
        assertEquals(ctxEnv.getValue(), "mark_laptop");
    }

    @Test
    public void testLoadEnvironmentVariableFromConfigServer() throws Exception {
    	TomcatLaunchConfigurer tomcatLaunchConfigurer = new TomcatLaunchConfigurer("http://localhost:8888", "application",
                new String[] { "default" });
        environmentVariables.set("CONFIG_TEST", "foobar");
        assertEquals("foobar", System.getenv("CONFIG_TEST"));
        PropertySource<?> source = tomcatLaunchConfigurer.getPropertySource();
        assertNotNull(source);
        ContextEnvironment ctxEnv = tomcatLaunchConfigurer.getEnvironment(source, "CONFIG_TEST");
        assertEquals(ctxEnv.getValue(), "foobar");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetResourceThrowsIllegalArgumentException() throws Exception {
        TomcatLaunchConfigurer helper = new TomcatLaunchConfigurer("", "", null);
        helper.createContainerDataSource(new HashMap<>());
    }

    @Test
    public void testGetValidResource() {
        HashMap<String, Object> credentials = new HashMap<>();
        credentials.put("name", "jdbc/serviceName");
        credentials.put("driverClassName", "com.mysql.cj.jdbc.Driver");
        credentials.put("url", "jdbc:mysql://root:password@localhost/mysql");
        credentials.put("username", "username");
        credentials.put("password", "password");
        credentials.put("factory", "org.apache.tomcat.jdbc.pool.DataSourceFactory");

        TomcatLaunchConfigurer helper = new TomcatLaunchConfigurer(null);
        helper.createContainerDataSource(credentials);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetEnvironmentThrowsIllegalArgumentException() throws Exception {
        TomcatLaunchConfigurer helper = new TomcatLaunchConfigurer("", "", null);
        helper.getEnvironment("test", null);
    }

    @Test
    public void testGetEnvironment() throws Exception {
        TomcatLaunchConfigurer helper = new TomcatLaunchConfigurer(null);
        ContextEnvironment env = helper.getEnvironment("test", "value");
        Assert.assertNotNull(env);
    }

}