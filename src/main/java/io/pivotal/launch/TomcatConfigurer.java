package io.pivotal.launch;

import io.pivotal.config.LocalConfigFileEnvironmentProcessor;
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
import org.springframework.cloud.config.client.ConfigClientProperties;
import org.springframework.cloud.config.client.ConfigServicePropertySourceLocator;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.Assert;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriTemplateHandler;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static io.pivotal.config.LocalConfigFileEnvironmentProcessor.APPLICATION_CONFIGURATION_PROPERTY_SOURCE_NAME;
import static io.pivotal.config.LocalConfigFileEnvironmentProcessor.DEFAULT_PROPERTIES;

/**
 * Created by malston on 3/20/17.
 */
public class TomcatConfigurer {

    private final TomcatLaunchHelper tomcatLaunchHelper = new TomcatLaunchHelper();

//    private final ConfigurableEnvironment environment = new StandardEnvironment();

    private ConfigurationLoader configurationLoader = null;

    public TomcatConfigurer() {
    }

    private String buildLibDir = null;

    private String buildClassDir = null;

    public TomcatConfigurer(ConfigurationLoader configurationLoader) {
        this.configurationLoader = configurationLoader;
        this.buildLibDir = "/build/libs/";
        this.buildClassDir = "build/classes/main";
    }

    public TomcatConfigurer(ConfigurationLoader configurationLoader, final String buildClassDir, final String buildLibDir) {
        this.configurationLoader = configurationLoader;
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

    //TODO add Gradle detection logic
    private boolean isGradle() {
        //CHECK IF GRADLE or MAVEN
        return true;
    }

    public ContextResource getResource(Map<String, Object> credentials) {
        return tomcatLaunchHelper.createContainerDataSource(credentials);
    }

    public ContextEnvironment getEnvironment(PropertySource source, String name) {
        Assert.notNull(source, "PropertySource cannot be null");
        Assert.notNull(source.getProperty(name), "Cannot find property with name: '" + "'");
        return tomcatLaunchHelper.getEnvironment(name, source.getProperty(name).toString());
    }

    public PropertySource loadConfiguration(String configServerUrl, String appName, String[] profiles) {
        if (this.configurationLoader == null) {
            return new DefaultConfigurationLoader(configServerUrl, appName, profiles).load();
        }
        return this.configurationLoader.load();
    }

    static class DefaultConfigurationLoader implements ConfigurationLoader {

        private static final String HTTPS_SCHEME = "https://";

        private static final String HTTP_SCHEME = "http://";

        private ConfigurableEnvironment environment = null;

        private ConfigClientProperties defaults = null;

        private ConfigServicePropertySourceLocator locator = null;

        private final RestTemplate restTemplate = new RestTemplate();

        private final LocalConfigFileEnvironmentProcessor localConfigFileEnvironmentProcessor = new LocalConfigFileEnvironmentProcessor();

        private String configServerUrl = null;

        DefaultConfigurationLoader(final String configServerUrl, final String app, final String[] profiles) {
            Assert.hasLength(configServerUrl, "You MUST set the config server URI");
            if (!configServerUrl.startsWith(HTTP_SCHEME) && !configServerUrl.startsWith(HTTPS_SCHEME)) {
                throw new RuntimeException("You MUST put the URI scheme in front of the config server URI");
            }
            this.configServerUrl = configServerUrl;
            this.environment = new StandardEnvironment();
            Map<String, Object> defaultProperties = new HashMap<>();
            defaultProperties.put("spring.application.name", app);
            this.environment.getPropertySources().addFirst(new MapPropertySource(DEFAULT_PROPERTIES, defaultProperties));
            for (String profile : profiles) {
                this.environment.addActiveProfile(profile);
            }
            this.defaults = new ConfigClientProperties(environment);
            this.defaults.setFailFast(false);
            this.defaults.setUri(this.configServerUrl);
            this.locator = new ConfigServicePropertySourceLocator(defaults);
            DefaultUriTemplateHandler uriTemplateHandler = new DefaultUriTemplateHandler();
            uriTemplateHandler.setBaseUrl(this.configServerUrl);
            this.restTemplate.setUriTemplateHandler(uriTemplateHandler);
            this.locator.setRestTemplate(restTemplate);
        }

        public PropertySource load() {
            PropertySource source = this.locator.locate(this.environment);
            if (source != null) {
                this.environment.getPropertySources().addFirst(source);
            }
            this.localConfigFileEnvironmentProcessor.processEnvironment(environment, source);

            return source == null ? this.environment.getPropertySources().get(APPLICATION_CONFIGURATION_PROPERTY_SOURCE_NAME) : source;
        }
    }

}
