package io.pivotal.config.client;

import org.springframework.core.env.PropertySource;

public interface PropertySourceProvider {

	PropertySource<?> getPropertySource();
}
