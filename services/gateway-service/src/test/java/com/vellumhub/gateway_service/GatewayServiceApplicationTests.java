package com.vellumhub.gateway_service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "JWT_PUBLIC_KEY_LOCATION=classpath:security/test-public.pem")
class GatewayServiceApplicationTests {

	@Autowired
	Environment environment;

	@Test
	void contextLoads() {
	}

	@Test
	void redisPropertiesRemainUnderSpringDataAfterJwtConfigurationChanges() {
		assertThat(environment.getProperty("spring.data.redis.host")).isNotBlank();
		assertThat(environment.getProperty("spring.data.redis.port")).isNotBlank();
		assertThat(environment.getProperty("jwt.data.redis.host")).isNull();
	}

}
