package io.pivotal.launch;

import org.apache.tomcat.util.descriptor.web.ContextEnvironment;
import org.apache.tomcat.util.descriptor.web.ContextResource;
import org.springframework.util.Assert;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Map;

public class TomcatLaunchHelper {
    public TomcatLaunchHelper() {
    }

    public File getRootFolder(String buildLibDir) {
        try {
            File root;
            String runningJarPath = TomcatLaunchHelper.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath().replaceAll("\\\\", "/");
            int lastIndexOf = runningJarPath.lastIndexOf(buildLibDir);
            if (lastIndexOf < 0) {
                root = new File("");
            } else {
                root = new File(runningJarPath.substring(0, lastIndexOf));
            }
            System.out.println("application resolved root folder: " + root.getAbsolutePath());
            return root;
        } catch (URISyntaxException ex) {
            throw new RuntimeException(ex);
        }
    }

    public ContextResource createContainerDataSource(Map<String, Object> credentials) {
        System.out.println("creds: " + credentials);
        Assert.notNull(credentials, "Service credentials cannot be null");
        Assert.notNull(credentials.get("name"), "Service name is null");
        Assert.notNull(credentials.get("driverClassName"), "Driver class name is null");
        Assert.notNull(credentials.get("url"), "Jdbc url is null");
        Assert.notNull(credentials.get("username"), "Username is null");
        Assert.notNull(credentials.get("password"), "Password is null");
        Assert.notNull(credentials.get("factory"), "DataSource factory is null");
        ContextResource resource = new ContextResource();
        resource.setAuth("Container");
        resource.setType("javax.sql.DataSource");
        resource.setName(credentials.get("name").toString());
        resource.setProperty("driverClassName", credentials.get("driverClassName"));
        resource.setProperty("url", credentials.get("url"));
        resource.setProperty("factory", credentials.get("factory"));
        resource.setProperty("username", credentials.get(("username")));
        resource.setProperty("password", credentials.get("password"));

        return resource;
    }

    public ContextEnvironment getEnvironment(String name, String value) {
        Assert.notNull(name, "Name cannot be null");
        Assert.notNull(value, "Value cannot be null");
        System.out.println("Setting key: '" + name + "'" + " to value: '" + value + "'");
        ContextEnvironment env = new ContextEnvironment();
        env.setName(name);
        env.setValue(value);
        env.setType("java.lang.String");
        env.setOverride(false);
        return env;
    }
}