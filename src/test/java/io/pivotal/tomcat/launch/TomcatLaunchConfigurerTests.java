package io.pivotal.tomcat.launch;

import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.ContextEnvironment;
import org.apache.tomcat.util.descriptor.web.ContextResource;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.springframework.core.env.PropertySource;

import java.util.HashMap;

import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.*;

public class TomcatLaunchConfigurerTests {

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
    public void testCreateStandardContext() throws Exception {
    	TomcatLaunchConfigurer tomcatLaunchConfigurer = new TomcatLaunchConfigurer("http://localhost:8888", "application",
                new String[] { "default" });
        Tomcat tomcat = new Tomcat();
        StandardContext ctx = tomcatLaunchConfigurer.createStandardContext(tomcat);
        assertNotNull(ctx);
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

    @Test(expected = IllegalArgumentException.class)
    public void testGetResourceThrowsIllegalArgumentException() throws Exception {
        TomcatLaunchConfigurer helper = new TomcatLaunchConfigurer("", "", null);
        helper.createContainerDataSource(new HashMap<>());
    }

    @Test
    public void testDefaultProperties() throws Exception {
        TomcatLaunchConfigurer tomcatLaunchConfigurer = new TomcatLaunchConfigurer("http://localhost", "foo", new String[] { });
        PropertySource<?> source = tomcatLaunchConfigurer.getPropertySource();
        assertNotNull(source);
        assertEquals("test", source.getProperty("testprop"));
        assertEquals("not in config server", source.getProperty("newprop"));
    }

    @Test
    public void testGetValidResource() {
        HashMap<String, Object> credentials = new HashMap<>();
        credentials.put("name", "jdbc/serviceName");
        credentials.put("driverClassName", "com.mysql.cj.jdbc.Driver");
        credentials.put("url", "jdbc:mysql://root:password@localhost/mysql");
        credentials.put("username", "malston");
        credentials.put("password", "m@lst0n");
        credentials.put("factory", "org.apache.tomcat.jdbc.pool.DataSourceFactory");

        TomcatLaunchConfigurer helper = new TomcatLaunchConfigurer(null);
        ContextResource cr = helper.createContainerDataSource(credentials);
        assertEquals("Container", cr.getAuth());
        assertEquals("javax.sql.DataSource", cr.getType());
        assertEquals("jdbc/serviceName", cr.getName());
        assertEquals("com.mysql.cj.jdbc.Driver", cr.getProperty("driverClassName"));
        assertEquals("jdbc:mysql://root:password@localhost/mysql", cr.getProperty("url"));
        assertEquals("malston", cr.getProperty("username"));
        assertEquals("m@lst0n", cr.getProperty("password"));
        assertEquals("org.apache.tomcat.jdbc.pool.DataSourceFactory", cr.getProperty("factory"));
        assertNull(cr.getProperty("connectionProperties"));
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
        assertNotNull(env);
    }

}