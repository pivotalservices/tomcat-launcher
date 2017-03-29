package io.pivotal.config.server;

import org.springframework.boot.Banner.Mode;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.config.server.EnableConfigServer;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
@EnableConfigServer
public class TestConfigServer {

	public static void main(String[] args) {
		start(args);
	}

	public static ConfigurableApplicationContext start(String... args) {
		return new SpringApplicationBuilder(TestConfigServer.class)
				.bannerMode(Mode.OFF)
				.properties("server.port=8888",
						"spring.cloud.config.server.git.uri=https://github.com/malston/config-repo",
						"spring.cloud.config.server.git.basedir=target/config")
				.run(args);
	}

}
