package io.pivotal.config.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.interceptor.RetryInterceptorBuilder;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

public class RetryConfiguration {

	private static Logger log = LoggerFactory.getLogger(RetryConfiguration.class);

	@Bean
	@ConditionalOnMissingBean(name = "configServerRetryInterceptor")
	public RetryOperationsInterceptor configServerRetryInterceptor() {
		log.info("Creating custom retry interceptor");
		return RetryInterceptorBuilder.stateless().backOffOptions(3000, 1.5, 10000)
				.maxAttempts(10).build();
	}

}
