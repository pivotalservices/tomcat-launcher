package io.pivotal.launch;

import io.pivotal.config.ConfigurationLoader;
import io.pivotal.config.DefaultConfigurationLoader;
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

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class TomcatConfigurer {

    private final TomcatLaunchHelper tomcatLaunchHelper = new TomcatLaunchHelper();

    private ConfigurationLoader configurationLoader = null;

    private String buildLibDir = null;

    private String buildClassDir = null;

    private TomcatConfigurer() {
        this.buildLibDir = "/build/libs/";
        this.buildClassDir = "build/classes/main";
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

    public TomcatConfigurer(final String configServerUrl, final ConfigurationLoader configurationLoader, final String buildClassDir, final String buildLibDir) {
        this(configServerUrl, configurationLoader);
        Assert.notNull(buildClassDir);
        Assert.notNull(buildLibDir);
        this.buildClassDir = buildClassDir;
        this.buildLibDir = buildLibDir;
    }

    public StandardContext createStandardContext(Tomcat tomcat) throws IOException, ServletException {
        File root = tomcatLaunchHelper.getRootFolder(buildLibDir);
        System.setProperty("org.apache.catalina.startup.EXIT_ON_INIT_FAILURE", "true");
        Path tempPath = Files.createTempDirectory("tomcat-base-dir");
        tomcat.setBaseDir(tempPath.toString());

        //The port that we should run on can be set into an environment variable
        //Look for that variable and default to 8080 if it isn't there.
        String webPort = System.getenv("PORT");
        if (webPort == null || webPort.isEmpty()) {
            webPort = "8080";
        }
        tomcat.setPort(Integer.valueOf(webPort));

        File webContentFolder = new File(root.getAbsolutePath(), "src/main/resources/");
        if (!webContentFolder.exists()) {
            //webContentFolder = Files.createTempDirectory("default-doc-base").toFile();
            webContentFolder = new File(root.getAbsolutePath());
        }
        System.out.println("webContentFolder is '" + webContentFolder.getAbsolutePath() + "'");
        StandardContext ctx = (StandardContext) tomcat.addWebapp("", webContentFolder.getAbsolutePath());

        //Set execution independent of current thread context classloader (compatibility with exec:java mojo)
        ctx.setParentClassLoader(TomcatLaunchHelper.class.getClassLoader());

        //Disable TLD scanning by default
        if (System.getProperty(Constants.SKIP_JARS_PROPERTY) == null && System.getProperty(Constants.SKIP_JARS_PROPERTY) == null) {
            System.out.println("disabling TLD scanning");
            StandardJarScanFilter jarScanFilter = (StandardJarScanFilter) ctx.getJarScanner().getJarScanFilter();
            jarScanFilter.setTldSkip("*");
        }

        System.out.println("configuring app with basedir: " + webContentFolder.getAbsolutePath());

        // Declare an alternative location for your "WEB-INF/classes" dir
        // Servlet 3.0 annotation will work
        File additionWebInfClassesFolder = new File(root.getAbsolutePath(), buildClassDir);
        WebResourceRoot resources = new StandardRoot(ctx);

        WebResourceSet resourceSet;
        if (additionWebInfClassesFolder.exists()) {
            resourceSet = new DirResourceSet(resources, "/WEB-INF/classes", additionWebInfClassesFolder.getAbsolutePath(), "/");
            System.out.println("loading WEB-INF/classes from '" + additionWebInfClassesFolder.getAbsolutePath() + "'");
        } else {
            additionWebInfClassesFolder = new File(root.getAbsolutePath());
            if (additionWebInfClassesFolder.exists()) {
                resourceSet = new DirResourceSet(resources, "/WEB-INF/classes", additionWebInfClassesFolder.getAbsolutePath(), "/");
                System.out.println("loading WEB-INF/classes from '" + additionWebInfClassesFolder.getAbsolutePath() + "'");
            } else {
                resourceSet = new EmptyResourceSet(resources);
            }
        }
        resources.addPreResources(resourceSet);
        ctx.setResources(resources);
        return ctx;
    }

    public ContextResource getResource(Map<String, Object> credentials) {
        return tomcatLaunchHelper.createContainerDataSource(credentials);
    }

    public ContextEnvironment getEnvironment(PropertySource source, String name) {
        Assert.notNull(source, "PropertySource cannot be null");
        Assert.notNull(source.getProperty(name), "Cannot find property with name: '" + "'");
        return tomcatLaunchHelper.getEnvironment(name, source.getProperty(name).toString());
    }

    public PropertySource loadConfiguration(String appName, String[] profiles) {
        return this.configurationLoader.load(appName, profiles);
    }

}
