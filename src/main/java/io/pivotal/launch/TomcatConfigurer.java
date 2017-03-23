package io.pivotal.launch;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

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

import io.pivotal.config.ConfigurationLoader;
import io.pivotal.config.DefaultConfigurationLoader;

public class TomcatConfigurer {

	private final TomcatLaunchHelper tomcatLaunchHelper = new TomcatLaunchHelper();

	private ConfigurationLoader configurationLoader = null;

	private String buildClassDir = null;

	private String relativeWebContentFolder = null;

	private String additionalLibFolder = null;

	private String pathToWebXml = null;
	
	private String webappPath = null;

	public final void setWebappPath(String webappPath) {
		this.webappPath = webappPath;
	}

	private TomcatConfigurer() {
		this.buildClassDir = "build/classes/main";
		this.relativeWebContentFolder = "src/main/resources/";
	}

	public TomcatConfigurer(final String configServerUrl) {
		this();
		this.configurationLoader = new DefaultConfigurationLoader(configServerUrl);
	}

	public TomcatConfigurer(final String configServerUrl, final ConfigurationLoader loader) {
		this(configServerUrl);
		Assert.notNull(loader);
		this.configurationLoader = loader;
	}

	public StandardContext createStandardContext(Tomcat tomcat) throws IOException, ServletException {
		File root = tomcatLaunchHelper.getRootFolder(relativeWebContentFolder);
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

		File webContentFolder = new File(root.getAbsolutePath(), relativeWebContentFolder);
		if (!webContentFolder.exists()) {
			// webContentFolder =
			// Files.createTempDirectory("default-doc-base").toFile();
			webContentFolder = new File(root.getAbsolutePath());
		}
		System.out.println("webContentFolder is '" + webContentFolder.getAbsolutePath() + "'");
		StandardContext ctx = (StandardContext) tomcat.addWebapp("", webContentFolder.getAbsolutePath());
		if (pathToWebXml != null && new File(pathToWebXml).exists()) {
			ctx.setDefaultWebXml(pathToWebXml);
		} else {
			ctx.setDefaultWebXml("org/apache/catalin/startup/NO_DEFAULT_XML");
		}
		
		if (webappPath != null) {
			File contextXmlFile = new File(webappPath + "/META-INF/context.xml");
		    if (contextXmlFile != null && contextXmlFile.exists()) {
		        ctx.setConfigFile(new URL(contextXmlFile.getAbsolutePath()));
		    }
		}

		// Set execution independent of current thread context classloader
		// (compatibility with exec:java mojo)
		ctx.setParentClassLoader(TomcatLaunchHelper.class.getClassLoader());

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
		File additionWebInfClassesFolder = new File(root.getAbsolutePath(), buildClassDir);
		WebResourceRoot resources = new StandardRoot(ctx);
		resources.addPreResources(
				addAdditionalWebInfResources(root, "/WEB-INF/classes", additionWebInfClassesFolder, resources));
		if (additionalLibFolder != null) {
			File additionWebInfLibFolder = new File(root.getAbsolutePath(), additionalLibFolder);
			resources.addPreResources(
					addAdditionalWebInfResources(root, "/WEB-INF/lib", additionWebInfLibFolder, resources));
		}

		ctx.setResources(resources);
		return ctx;
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

	public ContextResource getResource(Map<String, Object> credentials) {
		return tomcatLaunchHelper.createContainerDataSource(credentials);
	}

	public ContextEnvironment getEnvironment(PropertySource<?> source, String name) {
		Assert.notNull(source, "PropertySource cannot be null");
		Assert.notNull(source.getProperty(name), "Cannot find property with name: '" + name + "'");
		return tomcatLaunchHelper.getEnvironment(name, source.getProperty(name).toString());
	}

	public PropertySource<?> loadConfiguration(String appName, String[] profiles) {
		return this.configurationLoader.load(appName, profiles);
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

	public final void setPathToGlobalWebXml(String pathToGlobalWebXml) {
		Assert.notNull(pathToGlobalWebXml);
		this.pathToWebXml = pathToGlobalWebXml;
	}

}
