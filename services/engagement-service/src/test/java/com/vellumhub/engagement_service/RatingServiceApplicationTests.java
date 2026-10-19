package com.vellumhub.engagement_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "JWT_PUBLIC_KEY_LOCATION=classpath:security/test-public.pem")
class RatingServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
