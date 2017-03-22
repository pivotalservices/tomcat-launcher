package io.pivotal.launch;

import org.apache.tomcat.util.descriptor.web.ContextEnvironment;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;

public class TomcatLaunchHelperTest {

    @Test(expected = IllegalArgumentException.class)
    public void testGetResourceThrowsIllegalArgumentException() throws Exception {
        TomcatLaunchHelper helper = new TomcatLaunchHelper();
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

        TomcatLaunchHelper helper = new TomcatLaunchHelper();
        helper.createContainerDataSource(credentials);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetEnvironmentThrowsIllegalArgumentException() throws Exception {
        TomcatLaunchHelper helper = new TomcatLaunchHelper();
        helper.getEnvironment("test", null);
    }

    @Test
    public void testGetEnvironment() throws Exception {
        TomcatLaunchHelper helper = new TomcatLaunchHelper();
        ContextEnvironment env = helper.getEnvironment("test", "value");
        Assert.assertNotNull(env);
    }

}