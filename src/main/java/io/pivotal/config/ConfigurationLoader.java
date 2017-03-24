package io.pivotal.config;

import org.springframework.core.env.PropertySource;

public interface ConfigurationLoader {

    PropertySource<?> load();
}
