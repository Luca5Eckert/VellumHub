package com.vellumhub.catalog_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"spring.profiles.active=local", "JWT_PUBLIC_KEY_LOCATION=classpath:security/test-public.pem"})
class CatalogServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
