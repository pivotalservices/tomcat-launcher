package io.pivotal.config.client;

import org.springframework.core.env.PropertySource;

public interface ConfigClientOperations<T> {

	PropertySource<?> getPropertySource();
}
