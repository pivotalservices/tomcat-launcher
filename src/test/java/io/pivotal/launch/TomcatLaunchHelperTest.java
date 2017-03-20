package io.pivotal.launch;

import org.apache.tomcat.util.descriptor.web.ContextEnvironment;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;

public class TomcatLaunchHelperTest {

    @Test(expected = NullPointerException.class)
    public void testGetResource() throws Exception {
        TomcatLaunchHelper helper = new TomcatLaunchHelper();
        helper.getResource(new HashMap<>());
    }

    @Test
    public void testGetEnvironment() throws Exception {
        TomcatLaunchHelper helper = new TomcatLaunchHelper();
        ContextEnvironment env = helper.getEnvironment("test", "value");
        Assert.assertNotNull(env);
    }

}