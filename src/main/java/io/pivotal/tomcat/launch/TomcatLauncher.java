package io.pivotal.tomcat.launch;


import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.WebResourceSet;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.DirResourceSet;
import org.apache.catalina.webresources.EmptyResourceSet;
import org.apache.catalina.webresources.StandardRoot;
import org.apache.tomcat.JarScanner;
import org.apache.tomcat.util.descriptor.web.ContextResource;
import org.apache.tomcat.util.scan.Constants;
import org.apache.tomcat.util.scan.StandardJarScanFilter;
import org.apache.tomcat.util.scan.StandardJarScanner;
import org.springframework.util.Assert;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TomcatLauncher {

    private String buildClassDir = null;

    private String relativeWebContentFolder = null;

    private String additionalLibFolder = null;

    private String pathToWebXml = null;

    private String pathToContextXml = null;

    private String contextPath = null;

    private Context context;

    private final Tomcat tomcat;

    private List<ContextResource> contextResources = new ArrayList<>();

    private TomcatLauncher() {
        this.tomcat = new Tomcat();
    }

    public static TomcatConfigurer configure() {
        return new TomcatConfigurer(new TomcatLauncher());
    }

    public File getWebContentFolder() {
        return getRootFolder(this.getRelativeWebContentFolder());
    }

    private File getRootFolder(String path) {
        try {
            File root;
            String runningJarPath = TomcatLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI()
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

    public void launch() throws LifecycleException {
        tomcat.enableNaming();
        tomcat.start();

        // Must do this AFTER tomcat start is called (because of lifecycle hooks in tomcat)
        loadContextResources();

        tomcat.getServer().await();
    }

    private void loadContextResources() {
        for (ContextResource resource : contextResources) {
            getContext().getNamingResources().addResource(resource);
        }
    }

    public StandardContext createStandardContext() throws IOException, ServletException {
        File root = getWebContentFolder();
        System.setProperty("org.apache.catalina.startup.EXIT_ON_INIT_FAILURE", "true");
        Path tempPath = Files.createTempDirectory("tomcat-base-dir");
        this.setBaseDir(tempPath);

        // The port that we should run on can be set into an environment
        // variable
        // Look for that variable and default to 8080 if it isn't there.
        String webPort = System.getenv("PORT");
        if (webPort == null || webPort.isEmpty()) {
            webPort = "8080";
        }
        this.setPort(Integer.valueOf(webPort));

        StandardContext ctx = (StandardContext) addWebApp(root.getAbsolutePath());

        StandardJarScanner scanner = new StandardJarScanner();
        scanner.setScanBootstrapClassPath(true);
        addJarScanner(ctx, scanner);
        disableTldScanning(ctx);

        addDefaultContextXml(ctx);
        addDefaultWebXml(ctx);

        WebResourceRoot webResourceRoot = new StandardRoot(ctx);
        addWebInfClasses(webResourceRoot, this.getBuildClassDir());
        addAdditionalLibFolder(webResourceRoot, this.getAdditionalLibFolder());
        ctx.setResources(webResourceRoot);

        return ctx;
    }

    public void addWebInfClasses(WebResourceRoot resources, String relativePathToClasses) {
        if (relativePathToClasses != null) {
            File root = getWebContentFolder();
            File webInfClassesFolder = new File(root.getAbsolutePath(), relativePathToClasses);
            resources.addPreResources(
                    addAdditionalWebInfResources(root, "/WEB-INF/classes", webInfClassesFolder, resources));
        }
    }

    public void addAdditionalLibFolder(WebResourceRoot resources, String additionalLibFolder) {
        if (additionalLibFolder != null) {
            File root = getWebContentFolder();
            File additionWebInfLibFolder = new File(root.getAbsolutePath(), additionalLibFolder);
            resources.addPreResources(
                    addAdditionalWebInfResources(root, "/WEB-INF/lib", additionWebInfLibFolder, resources));
        }
    }

    public void disableTldScanning(Context ctx) {
        // Disable TLD scanning by default
        if (System.getProperty(Constants.SKIP_JARS_PROPERTY) == null
                && System.getProperty(Constants.SKIP_JARS_PROPERTY) == null) {
            System.out.println("disabling TLD scanning");
            StandardJarScanFilter jarScanFilter = (StandardJarScanFilter) ctx.getJarScanner().getJarScanFilter();
            jarScanFilter.setTldSkip("*");
        }
    }

    public void addDefaultWebXml(Context ctx) {
        if (this.getPathToWebXml() != null && new File(this.getPathToWebXml()).exists()) {
            ((StandardContext)ctx).setDefaultWebXml(this.getPathToWebXml());
        } else {
            ((StandardContext)ctx).setDefaultWebXml("org/apache/catalin/startup/NO_DEFAULT_XML");
        }
    }

    public void addDefaultContextXml(Context ctx) {
        System.out.println("pathToContextXml is '" + this.getPathToContextXml() + "'");
        if (this.getPathToContextXml() != null) {
            File contextXmlFile = new File(this.getPathToContextXml());
            if (contextXmlFile != null && contextXmlFile.exists()) {
                ctx.setConfigFile(
                        TomcatConfigurer.class.getClassLoader().getResource(contextXmlFile.getAbsolutePath()));
                ((StandardContext)ctx).setDefaultContextXml(contextXmlFile.getAbsolutePath());
                System.out.println("full path to context.xml is '" + contextXmlFile.getAbsolutePath() + "'");
            }
        }
    }

    public Context addWebApp(String absolutePath) throws ServletException {
        File webContentFolder = new File(absolutePath, this.getRelativeWebContentFolder());
        if (!webContentFolder.exists()) {
            webContentFolder = new File(absolutePath);
        }
        System.out.println("configuring app with basedir: " + webContentFolder.getAbsolutePath());
        Context ctx = tomcat.addWebapp(this.getContextPath(), webContentFolder.getAbsolutePath());
        // Set execution independent of current thread context classloader
        // (compatibility with exec:java mojo)
        ctx.setParentClassLoader(TomcatLauncher.class.getClassLoader());
        return ctx;
    }

    public void addJarScanner(Context ctx, JarScanner scanner) {
        ctx.setJarScanner(scanner);
    }

    public void setBaseDir(Path path) {
        tomcat.setBaseDir(path.toString());
    }

    public void setPort(int port) {
        tomcat.setPort(port);
    }

    public List<ContextResource> getContextResources() {
        return contextResources;
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

    public String getBuildClassDir() {
        return buildClassDir;
    }

    public final void setBuildClassDir(String buildClassDir) {
        this.buildClassDir = buildClassDir;
    }

    public String getRelativeWebContentFolder() {
        return relativeWebContentFolder;
    }

    public final void setRelativeWebContentFolder(String relativeWebContentFolder) {
        Assert.notNull(relativeWebContentFolder);
        this.relativeWebContentFolder = relativeWebContentFolder;
    }

    public String getAdditionalLibFolder() {
        return additionalLibFolder;
    }

    public final void setAdditionalLibFolder(String additionalLibFolder) {
        Assert.notNull(additionalLibFolder);
        this.additionalLibFolder = additionalLibFolder;
    }

    public String getPathToWebXml() {
        return pathToWebXml;
    }

    public final void setPathToWebXml(String pathToWebXml) {
        Assert.notNull(pathToWebXml);
        this.pathToWebXml = pathToWebXml;
    }

    public String getPathToContextXml() {
        return pathToContextXml;
    }

    public final void setPathToContextXml(String pathToContextXml) {
        this.pathToContextXml = pathToContextXml;
    }

    public String getContextPath() {
        return contextPath;
    }

    public final void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    public Context getContext() {
        return context;
    }

    public final void setContext(Context context) {
        this.context = context;
    }

    public WebResourceRoot getWebResourceRoot() {
        return this.getContext().getResources();
    }
}
