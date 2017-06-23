package io.pivotal.tomcat.launch;

import org.apache.catalina.Context;
import org.apache.catalina.core.StandardContext;
import org.apache.tomcat.util.descriptor.web.ContextEnvironment;
import org.junit.Test;
import org.springframework.core.env.PropertySource;

import static io.pivotal.tomcat.launch.TomcatLauncher.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TomcatLauncherTests {

    @Test
    public void shouldCreateStandardContext() throws Exception {
    	TomcatLauncher launcher = TomcatLauncher.configure().apply();
        Context ctx = launcher.getContext();
        assertNotNull(ctx);
    }

    @Test
    public void shouldGetPropertyValue() throws Exception {
        TomcatLauncher launcher = TomcatLauncher.configure()
                .addEnvironment(new PropertySource<String>("foo") {
                        public String getProperty(String name) {
                return "mark_laptop";
            }
                    }, "foo")
                .apply();
        ContextEnvironment env = launcher.getContext().getNamingResources().findEnvironment("foo");
        assertEquals(env.getValue(), "mark_laptop");
    }

    @Test(expected = IllegalArgumentException.class)
    public void addEnvironmentShouldThrowIllegalArgumentException() throws Exception {
        TomcatConfigurer tomcatConfigurer = TomcatLauncher.configure();
        tomcatConfigurer.addEnvironment("test", null);
    }

    @Test
    public void shouldFindContextEnvironment() throws Exception {
        TomcatLauncher launcher = TomcatLauncher.configure()
                .addEnvironment("test", "value").apply();
        ContextEnvironment env = launcher.getContext().getNamingResources().findEnvironment("test");
        assertNotNull(env);
        assertEquals("value", env.getValue());
    }

    @Test
    public void shouldCreateUnEqualDefaults() throws Exception {
        TomcatLauncher launcher1 = TomcatLauncher.configure()
                .buildClassFolder("path/classes")
                .webContentFolder("path/web")
                .contextPath("/test")
                .apply();
        TomcatLauncher launcher2 = TomcatLauncher.configure()
                .apply();
        assertThat(launcher1.getBuildClassDir(), is(not(equalTo(launcher2.getBuildClassDir()))));
        assertThat(launcher1.getRelativeWebContentFolder(), is(not(equalTo(launcher2.getRelativeWebContentFolder()))));
        assertThat(launcher1.getContextPath(), is(not(equalTo(launcher2.getContextPath()))));
    }

    @Test
    public void shouldCreateEqualDefaults() throws Exception {
        TomcatLauncher launcher1 = TomcatLauncher.configure()
                .buildClassFolder(DEFAULT_BUILD_DIR)
                .webContentFolder(DEFAULT_RELATIVE_WEB_CONTENT_FOLDER)
                .contextPath(DEFAULT_CONTEXT_PATH)
                .apply();
        TomcatLauncher launcher2 = TomcatLauncher.configure()
                .apply();
        assertThat(launcher1.getBuildClassDir(), is(equalTo(launcher2.getBuildClassDir())));
        assertThat(launcher1.getRelativeWebContentFolder(), is(equalTo(launcher2.getRelativeWebContentFolder())));
        assertThat(launcher1.getContextPath(), is(equalTo(launcher2.getContextPath())));
    }

    @Test
    public void shouldEqualSameContextXml() throws Exception {
        TomcatLauncher launcher1 = TomcatLauncher.configure().defaultContextXml("path/to/context.xml").apply();
        TomcatLauncher launcher2 = TomcatLauncher.configure().defaultContextXml("path/to/context.xml").apply();

        assertThat(((StandardContext)launcher1.getContext()).getDefaultContextXml(), is(equalTo(((StandardContext)launcher2.getContext()).getDefaultContextXml())));
    }

    @Test
    public void shouldEqualSameWebXml() throws Exception {
        TomcatLauncher launcher1 = TomcatLauncher.configure().defaultWebXml("path/to/web.xml").apply();
        TomcatLauncher launcher2 = TomcatLauncher.configure().defaultWebXml("path/to/web.xml").apply();

        assertThat(((StandardContext)launcher1.getContext()).getDefaultWebXml(), is(equalTo(((StandardContext)launcher2.getContext()).getDefaultWebXml())));
    }
}