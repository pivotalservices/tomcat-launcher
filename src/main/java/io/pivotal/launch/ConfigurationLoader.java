package io.pivotal.launch;

import org.springframework.core.env.PropertySource;

public interface ConfigurationLoader {

    PropertySource load(String appName, String[] profiles);
}
