package io.pivotal.tomcat.launch;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import javax.servlet.ServletException;

import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.WebResourceSet;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.DirResourceSet;
import org.apache.catalina.webresources.EmptyResourceSet;
import org.apache.catalina.webresources.StandardRoot;
import org.apache.tomcat.util.descriptor.web.ContextEnvironment;
import org.apache.tomcat.util.descriptor.web.ContextResource;
import org.apache.tomcat.util.scan.Constants;
import org.apache.tomcat.util.scan.StandardJarScanFilter;
import org.springframework.core.env.PropertySource;
import org.springframework.util.Assert;

import io.pivotal.config.client.PropertySourceProvider;
import io.pivotal.config.client.ConfigClientTemplate;

public class TomcatLaunchConfigurer {

	private String buildClassDir = null;

	private String relativeWebContentFolder = null;

	private String additionalLibFolder = null;

	private String pathToWebXml = null;

	private String pathToContextXml = null;

	private final PropertySourceProvider configClient;

	public TomcatLaunchConfigurer(final PropertySourceProvider configClient) {
		this.configClient = configClient;
	}

	public TomcatLaunchConfigurer(final String configServerUrl, final String app, final String[] profiles) {
		this.configClient = new ConfigClientTemplate<Object>(configServerUrl, app, profiles);
		this.buildClassDir = "build/classes/main";
		this.relativeWebContentFolder = "src/main/resources/";
	}

	public StandardContext createStandardContext(Tomcat tomcat) throws IOException, ServletException {
		File root = getRootFolder(this.relativeWebContentFolder);
		System.setProperty("org.apache.catalina.startup.EXIT_ON_INIT_FAILURE", "true");
		Path tempPath = Files.createTempDirectory("tomcat-base-dir");
		tomcat.setBaseDir(tempPath.toString());

		// The port that we should run on can be set into an environment
		// variable
		// Look for that variable and default to 8080 if it isn't there.
		String webPort = System.getenv("PORT");
		if (webPort == null || webPort.isEmpty()) {
			webPort = "8080";
		}
		tomcat.setPort(Integer.valueOf(webPort));

		File webContentFolder = new File(root.getAbsolutePath(), this.relativeWebContentFolder);
		if (!webContentFolder.exists()) {
			webContentFolder = new File(root.getAbsolutePath());
		}
		StandardContext ctx = (StandardContext) tomcat.addWebapp("", webContentFolder.getAbsolutePath());
		System.out.println("pathToContextXml is '" + this.pathToContextXml + "'");
		if (this.pathToContextXml != null) {
			File contextXmlFile = new File(this.pathToContextXml);
			if (contextXmlFile != null && contextXmlFile.exists()) {
				ctx.setConfigFile(
						TomcatLaunchConfigurer.class.getClassLoader().getResource(contextXmlFile.getAbsolutePath()));
				ctx.setDefaultContextXml(contextXmlFile.getAbsolutePath());
				System.out.println("full path to context.xml is '" + contextXmlFile.getAbsolutePath() + "'");
			}
		}

		if (this.pathToWebXml != null && new File(this.pathToWebXml).exists()) {
			ctx.setDefaultWebXml(this.pathToWebXml);
		} else {
			ctx.setDefaultWebXml("org/apache/catalin/startup/NO_DEFAULT_XML");
		}

		// Set execution independent of current thread context classloader
		// (compatibility with exec:java mojo)
		ctx.setParentClassLoader(TomcatLaunchConfigurer.class.getClassLoader());

		// Disable TLD scanning by default
		if (System.getProperty(Constants.SKIP_JARS_PROPERTY) == null
				&& System.getProperty(Constants.SKIP_JARS_PROPERTY) == null) {
			System.out.println("disabling TLD scanning");
			StandardJarScanFilter jarScanFilter = (StandardJarScanFilter) ctx.getJarScanner().getJarScanFilter();
			jarScanFilter.setTldSkip("*");
		}

		System.out.println("configuring app with basedir: " + webContentFolder.getAbsolutePath());

		// Declare an alternative location for your "WEB-INF/classes" dir
		// Servlet 3.0 annotation will work
		File additionWebInfClassesFolder = new File(root.getAbsolutePath(), this.buildClassDir);
		WebResourceRoot resources = new StandardRoot(ctx);
		resources.addPreResources(
				addAdditionalWebInfResources(root, "/WEB-INF/classes", additionWebInfClassesFolder, resources));
		if (this.additionalLibFolder != null) {
			File additionWebInfLibFolder = new File(root.getAbsolutePath(), this.additionalLibFolder);
			resources.addPreResources(
					addAdditionalWebInfResources(root, "/WEB-INF/lib", additionWebInfLibFolder, resources));
		}

		ctx.setResources(resources);
		return ctx;
	}

	private File getRootFolder(String path) {
		try {
			File root;
			String runningJarPath = TomcatLaunchConfigurer.class.getProtectionDomain().getCodeSource().getLocation().toURI()
					.getPath().replaceAll("\\\\", "/");
			int lastIndexOf = runningJarPath.lastIndexOf(path);
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

	public PropertySource<?> getPropertySource() {
		return this.configClient.getPropertySource();
	}

	public ContextResource createContainerDataSource(Map<String, Object> credentials) {
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
	
	public ContextEnvironment getEnvironment(PropertySource<?> source, String name) {
		Assert.notNull(source, "PropertySource cannot be null");
		Assert.notNull(source.getProperty(name), "Cannot find property with name: '" + name + "'");
		return getEnvironment(name, source.getProperty(name).toString());
	}
	
	private WebResourceSet addAdditionalWebInfResources(File root, String webAppMount, File additionWebInfClassesFolder,
			WebResourceRoot resources) {
		WebResourceSet resourceSet;
		if (additionWebInfClassesFolder.exists()) {
			resourceSet = new DirResourceSet(resources, webAppMount, additionWebInfClassesFolder.getAbsolutePath(),
					"/");
			System.out.println(
					"loading " + webAppMount + " from '" + additionWebInfClassesFolder.getAbsolutePath() + "'");
		} else {
			additionWebInfClassesFolder = new File(root.getAbsolutePath());
			if (additionWebInfClassesFolder.exists()) {
				resourceSet = new DirResourceSet(resources, webAppMount, additionWebInfClassesFolder.getAbsolutePath(),
						"/");
				System.out.println(
						"loading " + webAppMount + " from '" + additionWebInfClassesFolder.getAbsolutePath() + "'");
			} else {
				resourceSet = new EmptyResourceSet(resources);
			}
		}
		return resourceSet;
	}

	public final void setBuildClassDir(String buildClassDir) {
		this.buildClassDir = buildClassDir;
	}

	public final void setRelativeWebContentFolder(String relativeWebContentFolder) {
		Assert.notNull(relativeWebContentFolder);
		this.relativeWebContentFolder = relativeWebContentFolder;
	}

	public final void setAdditionalLibFolder(String additionalLibFolder) {
		Assert.notNull(additionalLibFolder);
		this.additionalLibFolder = additionalLibFolder;
	}

	public final void setPathToWebXml(String pathToWebXml) {
		Assert.notNull(pathToWebXml);
		this.pathToWebXml = pathToWebXml;
	}

	public final void setPathToContextXml(String pathToContextXml) {
		this.pathToContextXml = pathToContextXml;
	}

}