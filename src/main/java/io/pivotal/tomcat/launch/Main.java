package io.pivotal.tomcat.launch;

import org.apache.tomcat.util.descriptor.web.ContextResource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.Map;

public class Main {

	public static final String PREFIX_JDBC = "jdbc/";

	public static void main(String[] args) throws Exception {
		Main main = new Main();
        main.run();
	}

	public void run() throws Exception {
        Map<String, Object> properties = new HashMap<>();
        properties.put("foo", "bar");
        properties.put("newprop", "some property string");
        properties.put("foo.db", "mycooldb");
		PropertySource source = new MapPropertySource("foo", properties);

		TomcatLauncher.configure()
                .withStandardContext()
				.addEnvironment(source, "foo")
				.addEnvironment(source, "newprop")
                .addEnvironment(source, "foo.db")
                .addContextResource(createContainerDataSource(getConnectionProperties("hello-db")))
                .apply()
                .launch();
	}

    private ContextResource createContainerDataSource(Map<String, Object> credentials) {
        System.out.println("creds: " + credentials);
        Assert.notNull(credentials, "Service credentials cannot be null");
        Assert.notNull(credentials.get("name"), "Service name is null");
        Assert.notNull(credentials.get("driverClassName"), "Driver class name is null");
        Assert.notNull(credentials.get("url"), "Jdbc url is null");
        Assert.notNull(credentials.get("username"), "Username is null");
        Assert.notNull(credentials.get("password"), "Password is null");
        ContextResource resource = new ContextResource();
        resource.setAuth("Container");
        resource.setType("javax.sql.DataSource");
        resource.setName(credentials.get("name").toString());
        resource.setProperty("driverClassName", credentials.get("driverClassName"));
        resource.setProperty("url", credentials.get("url"));
        if (credentials.get("factory") != null) {
            resource.setProperty("factory", credentials.get("factory"));
        }
        if (credentials.get("connectionProperties") != null) {
            resource.setProperty("connectionProperties", credentials.get("connectionProperties"));
        }
        resource.setProperty("username", credentials.get(("username")));
        resource.setProperty("password", credentials.get("password"));

        return resource;
    }

    private Map<String, Object> getConnectionProperties(String serviceName) {
        Map<String, Object> credentials = new HashMap<>();
        credentials.put("url", "jdbc:mysql://localhost/mysql?useSSL=false");
        credentials.put("username", "root");
        credentials.put("password", "password");
        credentials.put("connectionProperties", "useUnicode=true;useJDBCCompliantTimezoneShift=true;useLegacyDatetimeCode=false;serverTimezone=UTC;");
        credentials.put("driverClassName", "com.mysql.cj.jdbc.Driver");
        credentials.put("name", PREFIX_JDBC + serviceName);
        credentials.put("factory", "org.apache.tomcat.jdbc.pool.DataSourceFactory");
        return credentials;
    }
}
