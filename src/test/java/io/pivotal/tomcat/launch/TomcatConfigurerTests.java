package io.pivotal.tomcat.launch;

import org.apache.catalina.Context;
import org.apache.tomcat.util.descriptor.web.ContextEnvironment;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.springframework.core.env.PropertySource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TomcatConfigurerTests {

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Test
    public void testCreateStandardContext() throws Exception {
    	TomcatLauncher launcher = TomcatLauncher.configure().withStandardContext().apply();
        Context ctx = launcher.getContext();
        assertNotNull(ctx);
    }

    @Test
    public void testGetProperty() throws Exception {
        TomcatConfigurer tomcatConfigurer = TomcatLauncher.configure();
        ContextEnvironment ctxEnv = tomcatConfigurer.getEnvironment(new PropertySource<String>("foo") {
            public String getProperty(String name) {
                return "mark_laptop";
            }
        }, "foo");
        assertEquals(ctxEnv.getValue(), "mark_laptop");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetEnvironmentThrowsIllegalArgumentException() throws Exception {
        TomcatConfigurer tomcatConfigurer = TomcatLauncher.configure();
        tomcatConfigurer.getEnvironment("test", null);
    }

    @Test
    public void testGetEnvironment() throws Exception {
        TomcatConfigurer tomcatConfigurer = TomcatLauncher.configure();
        ContextEnvironment env = tomcatConfigurer.getEnvironment("test", "value");
        assertNotNull(env);
    }

}