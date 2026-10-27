package com.vellumhub.user_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"spring.profiles.active=local", "JWT_JWKS_URI=http://127.0.0.1:1/oauth2/jwks"})
class UserServiceApplicationTests {


	@Test
	void contextLoads() {
	}

}
