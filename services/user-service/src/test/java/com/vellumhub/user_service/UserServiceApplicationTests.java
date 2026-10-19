package com.vellumhub.user_service;

import org.junit.jupiter.api.Test;
import com.vellumhub.user_service.share.security.token.TestRsaKeyMaterial;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"spring.profiles.active=local"})
class UserServiceApplicationTests {

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        registry.add("JWT_PUBLIC_KEY_LOCATION", TestRsaKeyMaterial::publicLocation);
        registry.add("JWT_PRIVATE_KEY_LOCATION", TestRsaKeyMaterial::privateLocation);
    }

	@Test
	void contextLoads() {
	}

}
