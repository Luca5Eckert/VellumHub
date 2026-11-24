package com.vellumhub.gateway_service.config;

import io.netty.resolver.dns.NoopDnsCache;
import java.time.Duration;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class UpstreamHttpClientConfig {

    @Bean
    HttpClientCustomizer upstreamDnsResolver() {
        // Compose can assign an upstream's old IP to a different service.
        return client -> client.resolver(resolver -> resolver
                .resolveCache(NoopDnsCache.INSTANCE)
                .cacheMaxTimeToLive(Duration.ofSeconds(1)));
    }
}
