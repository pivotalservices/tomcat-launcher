package io.pivotal.launch;

import java.io.IOException;
import java.util.Map;

import javax.servlet.ServletException;

import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.ContextEnvironment;
import org.apache.tomcat.util.descriptor.web.ContextResource;
import org.springframework.core.env.PropertySource;
import org.springframework.util.Assert;

import io.pivotal.config.client.ConfigClientOperations;
import io.pivotal.config.client.ConfigClientTemplate;

public class TomcatConfigurer {

	private final TomcatLaunchHelper tomcatLaunchHelper = new TomcatLaunchHelper("build/classes/main", "src/main/resources/");

	private ConfigClientOperations<?> configClient = null;

	public TomcatConfigurer(final String configServerUrl, final String app, final String[] profiles) {
		this.configClient = new ConfigClientTemplate<Object>(configServerUrl, app, profiles);
	}
	
	public StandardContext createStandardContext(Tomcat tomcat) throws IOException, ServletException {
		return tomcatLaunchHelper.createStandardContext(tomcat);
	}
	
	public ContextResource createContainerDataSource(Map<String, Object> credentials) {
		return tomcatLaunchHelper.createContainerDataSource(credentials);
	}

	public ContextEnvironment getEnvironment(PropertySource<?> source, String name) {
		Assert.notNull(source, "PropertySource cannot be null");
		Assert.notNull(source.getProperty(name), "Cannot find property with name: '" + name + "'");
		return tomcatLaunchHelper.getEnvironment(name, source.getProperty(name).toString());
	}

	public PropertySource<?> getPropertySource() {
		return this.configClient.getPropertySource();
	}

	public final ConfigClientOperations<?> getConfigClientOperations() {
		return configClient;
	}

	public final void setConfigClientOperations(ConfigClientOperations<?> configurationLoader) {
		this.configClient = configurationLoader;
	}

	public ContextEnvironment getEnvironment(String name, String value) {
		return tomcatLaunchHelper.getEnvironment(name, value);
	}

	public final void setBuildClassDir(String buildClassDir) {
		tomcatLaunchHelper.setBuildClassDir(buildClassDir);
	}

	public final void setRelativeWebContentFolder(String relativeWebContentFolder) {
		tomcatLaunchHelper.setRelativeWebContentFolder(relativeWebContentFolder);
	}

	public final void setAdditionalLibFolder(String additionalLibFolder) {
		tomcatLaunchHelper.setAdditionalLibFolder(additionalLibFolder);
	}

	public final void setPathToWebXml(String pathToWebXml) {
		tomcatLaunchHelper.setPathToWebXml(pathToWebXml);
	}

	public final void setPathToContextXml(String pathToContextXml) {
		tomcatLaunchHelper.setPathToContextXml(pathToContextXml);
	}
}
