package io.pivotal.tomcat.launch;

import org.apache.catalina.Context;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.webresources.StandardRoot;
import org.apache.tomcat.util.descriptor.web.ContextEnvironment;
import org.apache.tomcat.util.descriptor.web.ContextResource;
import org.apache.tomcat.util.scan.StandardJarScanner;
import org.springframework.core.env.PropertySource;
import org.springframework.util.Assert;

import javax.servlet.ServletException;
import java.io.IOException;
import java.nio.file.Path;

public class TomcatConfigurer {

    private TomcatLauncher launcher;

    public TomcatConfigurer(TomcatLauncher launcher) {
        this.launcher = launcher;
        launcher.setBuildClassDir("build/classes/main");
        launcher.setRelativeWebContentFolder("src/main/webapp");
        launcher.setContextPath("");
    }

    public TomcatConfigurer withStandardContext() throws IOException, ServletException {
        Context ctx = launcher.createStandardContext();
        launcher.setContext(ctx);
        return this;
    }

    public TomcatConfigurer withContext(Context context) {
        launcher.setContext(context);
        return this;
    }

    public TomcatConfigurer baseDir(Path baseDir) {
        launcher.setBaseDir(baseDir);
        return this;
    }

    public TomcatConfigurer port(int port) {
        launcher.setPort(port);
        return this;
    }

    public TomcatConfigurer addWebApp() throws ServletException {
        Context context = launcher.addWebApp(launcher.getWebContentFolder().getAbsolutePath());
        launcher.setContext(context);
        return this;
    }

    public TomcatConfigurer jarScanner() {
        if (launcher.getContext() != null) {
            StandardJarScanner scanner = new StandardJarScanner();
            scanner.setScanBootstrapClassPath(true);
            launcher.addJarScanner(launcher.getContext(), scanner);
        }
        return this;
    }

    public TomcatConfigurer disableTldScanning() {
        if (launcher.getContext() != null) {
            launcher.disableTldScanning(launcher.getContext());
        }
        return this;
    }

    public TomcatConfigurer defaultContextXml() {
        if (launcher.getContext() != null) {
            launcher.addDefaultContextXml(launcher.getContext());
        }
        return this;
    }

    public TomcatConfigurer defaultWebXml() {
        if (launcher.getContext() != null) {
            launcher.addDefaultWebXml(launcher.getContext());
        }
        return this;
    }

    public TomcatConfigurer webInfClassDir(String classDir) {
        if (launcher.getContext() != null) {
            WebResourceRoot webResourceRoot = launcher.getWebResourceRoot();
            if (webResourceRoot == null) {
                webResourceRoot = new StandardRoot(launcher.getContext());
                launcher.getContext().setResources(webResourceRoot);
            }
            launcher.addWebInfClasses(webResourceRoot, classDir);
        }
        return this;
    }

    public TomcatConfigurer additionalLibDir(String libDir) {
        if (launcher.getContext() != null) {
            WebResourceRoot webResourceRoot = launcher.getWebResourceRoot();
            if (webResourceRoot == null) {
                webResourceRoot = new StandardRoot(launcher.getContext());
                launcher.getContext().setResources(webResourceRoot);
            }
            launcher.addAdditionalLibFolder(webResourceRoot, libDir);
        }
        return this;
    }

    public TomcatConfigurer addEnvironment(PropertySource source, String key) {
        launcher.getContext().getNamingResources().addEnvironment(this.getEnvironment(source, key));
        return this;
    }

    public TomcatConfigurer addContextResource(ContextResource resource) {
        launcher.getContextResources().add(resource);
        return this;
    }

    public TomcatLauncher apply() throws IOException {
        return launcher;
    }

    private ContextEnvironment getEnvironment(String name, String value, String type, boolean override) {
        Assert.notNull(name, "Name cannot be null");
        Assert.notNull(value, "Value cannot be null");
        System.out.println("Setting key: '" + name + "'" + " to value: '" + value + "'");
        ContextEnvironment env = new ContextEnvironment();
        env.setName(name);
        env.setValue(value);
        env.setType(type);
        env.setOverride(override);
        return env;
    }

    public ContextEnvironment getEnvironment(String name, String value) {
        return getEnvironment(name, value, "java.lang.String", false);
    }

    public ContextEnvironment getEnvironment(PropertySource<?> source, String name, String type, boolean override) {
        Assert.notNull(source, "PropertySource cannot be null");
        Assert.notNull(source.getProperty(name), "Cannot find property with name: '" + name + "'");
        return getEnvironment(name, source.getProperty(name).toString(), type, override);
    }

    public ContextEnvironment getEnvironment(PropertySource<?> source, String name) {
        return getEnvironment(name, source.getProperty(name).toString());
    }
}