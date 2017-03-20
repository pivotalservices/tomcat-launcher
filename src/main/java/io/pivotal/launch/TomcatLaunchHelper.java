package io.pivotal.launch;

import org.apache.tomcat.util.descriptor.web.ContextEnvironment;
import org.apache.tomcat.util.descriptor.web.ContextResource;

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

    /**
     * TODO: populate by traversing keys
     */
    public ContextResource getResource(Map<String, Object> credentials) {
        System.out.println("creds: " + credentials);
        ContextResource resource = new ContextResource();
        resource.setName(credentials.get("serviceName").toString());
        resource.setAuth("Container");
        resource.setType("javax.sql.DataSource");
        resource.setProperty("driverClassName", credentials.get("driverClassName"));
        resource.setProperty("url", credentials.get("jdbcUrl"));
        resource.setProperty("factory", "org.apache.tomcat.jdbc.pool.DataSourceFactory");
        resource.setProperty("username", credentials.get(("username")));
        resource.setProperty("password", credentials.get("password"));

        return resource;
    }

    public ContextEnvironment getEnvironment(String name, String value) {
        System.out.println("Setting key: '" + name + "'" + " to value: '" + value + "'");
        ContextEnvironment env = new ContextEnvironment();
        env.setName(name);
        env.setValue(value);
        env.setType("java.lang.String");
        env.setOverride(false);
        return env;
    }
}