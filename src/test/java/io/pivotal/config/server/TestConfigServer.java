/*
 * Copyright 2014-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
