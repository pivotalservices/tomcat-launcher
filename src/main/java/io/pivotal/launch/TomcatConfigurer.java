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
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriTemplateHandler;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static io.pivotal.config.LocalConfigFileEnvironmentProcessor.APPLICATION_CONFIGURATION_PROPERTY_SOURCE_NAME;

/**
 * Created by malston on 3/20/17.
 */
public class TomcatConfigurer {

    private final TomcatLaunchHelper tomcatLaunchHelper = new TomcatLaunchHelper();

    public StandardContext createStandardContext(Tomcat tomcat) throws IOException, ServletException {
        String buildLibDir = null;
        String buildClassDir = null;
        if (isGradle()) {
            buildLibDir = "/build/libs/";
            buildClassDir = "build/classes/main";
        }
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
        return tomcatLaunchHelper.getResource(credentials);
    }

    public ContextEnvironment getEnvironment(PropertySource source, String name) {
        return tomcatLaunchHelper.getEnvironment(name, source.getProperty(name).toString());
    }

    public PropertySource loadConfiguration(String configServerUrl) {
        return new ConfigurationLoader().load(configServerUrl);
    }

    static class ConfigurationLoader {

        public static final String HTTPS_SCHEME = "https://";

        public static final String HTTP_SCHEME = "http://";

        private final ConfigurableEnvironment environment = new StandardEnvironment();

        private ConfigServicePropertySourceLocator locator;

        private final RestTemplate restTemplate = new RestTemplate();

        private final LocalConfigFileEnvironmentProcessor localConfigFileEnvironmentProcessor = new LocalConfigFileEnvironmentProcessor();

        ConfigurationLoader() {}

        PropertySource load(String configServerUrl) {
            if (configServerUrl == null || configServerUrl.isEmpty()) {
                throw new RuntimeException("You MUST set the config server URI");
            }
            if (!configServerUrl.startsWith(HTTP_SCHEME) && !configServerUrl.startsWith(HTTPS_SCHEME)) {
                throw new RuntimeException("You MUST put the URI scheme in front of the config server URI");
            }
            System.out.println("configServerUrl is '" + configServerUrl + "'");
            ConfigClientProperties defaults = new ConfigClientProperties(this.environment);
            defaults.setFailFast(false);
            defaults.setUri(configServerUrl);
            DefaultUriTemplateHandler uriTemplateHandler = new DefaultUriTemplateHandler();
            uriTemplateHandler.setBaseUrl(configServerUrl);
            this.restTemplate.setUriTemplateHandler(uriTemplateHandler);
            this.locator = new ConfigServicePropertySourceLocator(defaults);
            this.locator.setRestTemplate(restTemplate);
            PropertySource source = this.locator.locate(this.environment);
            if (source != null) {
                this.environment.getPropertySources().addFirst(source);
            }
            this.localConfigFileEnvironmentProcessor.processEnvironment(environment, source);

            return source == null ? this.environment.getPropertySources().get(APPLICATION_CONFIGURATION_PROPERTY_SOURCE_NAME) : source;
        }
    }

}
