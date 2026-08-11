package com.vellumhub.recommendation_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {"spring.profiles.active=local", "JWT_KEY=dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLXB1cnBvc2VzLXdpdGgtYXQtbGVhc3QtMzItaXRz"})
@ActiveProfiles("test")
class RecommendationServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
