package io.pivotal.launch;

import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.core.env.PropertySource;

public class TomcatConfigurerTest {

    @Test(expected = RuntimeException.class)
    public void testRunWithNoConfigServerUrl() throws Exception {
        TomcatConfigurer tomcatConfigurer = new TomcatConfigurer();
        tomcatConfigurer.loadConfiguration("");
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
    }

    @Test
    public void testLoadLocalConfigurationFromFile() throws Exception {
        TomcatConfigurer tomcatConfigurer = new TomcatConfigurer();
        PropertySource source = tomcatConfigurer.loadConfiguration("http://bogus");
        Assert.assertNotNull(source);
    }

}