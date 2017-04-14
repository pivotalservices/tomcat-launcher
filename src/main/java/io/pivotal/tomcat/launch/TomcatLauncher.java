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

    public static final String DEFAULT_BUILD_DIR = "build/classes/main";

    public static final String DEFAULT_RELATIVE_WEB_CONTENT_FOLDER = "src/main/webapp";

    public static final String DEFAULT_CONTEXT_PATH = "";

    private String buildClassDir = DEFAULT_BUILD_DIR;

    private String relativeWebContentFolder = DEFAULT_RELATIVE_WEB_CONTENT_FOLDER;

    private String contextPath = DEFAULT_CONTEXT_PATH;

    private String additionalLibFolder = null;

    private String pathToWebXml = null;

    private String pathToContextXml = null;

    private Context context;

    private final Tomcat tomcat;

    private List<ContextResource> contextResources = new ArrayList<>();

    private TomcatLauncher() {
        this.tomcat = new Tomcat();
    }

    public static TomcatConfigurer configure() {
        return new TomcatConfigurer(new TomcatLauncher());
    }

    private File getWebContentFolder() {
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

        StandardContext ctx = (StandardContext) addWebApp();

        StandardJarScanner scanner = new StandardJarScanner();
        scanner.setScanBootstrapClassPath(true);
        addJarScanner(ctx, scanner);
        disableTldScanning(ctx);

        addDefaultContextXml(ctx, this.getPathToContextXml());
        addDefaultWebXml(ctx, this.getPathToWebXml());

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

    public void addDefaultWebXml(Context ctx, String pathToWebXml) {
        if (pathToWebXml != null) {
            System.out.println("pathToWebXml is '" + pathToWebXml + "'");
            File root = getWebContentFolder();
            File webXmlFile = new File(root.getAbsolutePath(), pathToWebXml);
            if (webXmlFile.exists()) {
                System.out.println("Full path to web.xml is '" + webXmlFile.getAbsolutePath() + "'");
                ((StandardContext) ctx).setDefaultWebXml(webXmlFile.getAbsolutePath());
            } else {
                ((StandardContext) ctx).setDefaultWebXml("org/apache/catalin/startup/NO_DEFAULT_XML");
            }
        }
    }

    public void addDefaultContextXml(Context ctx, String pathToContextXml) {
        if (pathToContextXml != null) {
            System.out.println("pathToContextXml is '" + pathToContextXml + "'");
            File root = getWebContentFolder();
            File contextXmlFile = new File(root.getAbsolutePath(), pathToContextXml);
            if (contextXmlFile.exists()) {
                System.out.println("Full path to context.xml is '" + contextXmlFile.getAbsolutePath() + "'");
                ctx.setConfigFile(
                        TomcatConfigurer.class.getClassLoader().getResource(contextXmlFile.getAbsolutePath()));
                ((StandardContext)ctx).setDefaultContextXml(contextXmlFile.getAbsolutePath());
            }
        }
    }

    public Context addWebApp() throws ServletException {
        return this.addWebApp(this.getContextPath(), this.getRelativeWebContentFolder());
    }

    private Context addWebApp(String contextPath, String relativeWebContentFolder) throws ServletException {
        String absolutePath = getWebContentFolder().getAbsolutePath();
        File webContentFolder = new File(absolutePath, relativeWebContentFolder);
        if (!webContentFolder.exists()) {
            webContentFolder = new File(absolutePath);
        }
        System.out.println("configuring app with basedir: " + webContentFolder.getAbsolutePath());
        Context ctx = tomcat.addWebapp(contextPath, webContentFolder.getAbsolutePath());
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
