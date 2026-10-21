package com.vellumhub.recommendation_service.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AFF-REC-009 static companion to the live upstream-attempt probe.
 * Protects Recommendation's serving path against new synchronous HTTP adapters.
 * Other modules are deliberately not restricted by this rule.
 */
class RecommendationReadAutonomyArchitectureFitnessTest {
    private static final String ROOT = "com.vellumhub.recommendation_service";
    private static final String[] HTTP_CLIENT_PACKAGES = {
            "org.springframework.web.client..",
            "org.springframework.web.reactive.function.client..",
            "feign..",
            "java.net.http..",
            "java.net.HttpURLConnection",
            "okhttp3..",
            "org.apache.http..",
            "org.apache.hc.client.."
    };

    @Test
    void recommendationReadPathMustNotDependOnSynchronousHttpClients() {
        JavaClasses production = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(ROOT);
        ArchRule rule = noClasses().that().resideInAnyPackage(
                        "..module.recommendation..",
                        "..share.provider..")
                .should().dependOnClassesThat().resideInAnyPackage(HTTP_CLIENT_PACKAGES)
                .because("AFF-REC-009: materialized recommendation reads must not require synchronous HTTP")
                .allowEmptyShould(false);
        rule.check(production);
    }

    @Test
    void deliberateHttpDependencyIsRejected() {
        JavaClasses fixture = new ClassFileImporter().importClasses(ForbiddenHttpFixture.class);
        ArchRule rule = noClasses().should().dependOnClassesThat()
                .resideInAnyPackage(HTTP_CLIENT_PACKAGES)
                .because("AFF-REC-009 negative fixture")
                .allowEmptyShould(false);

        assertThatThrownBy(() -> rule.check(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("HttpClient");
    }

    static class ForbiddenHttpFixture {
        java.net.http.HttpClient prohibited() {
            return java.net.http.HttpClient.newHttpClient();
        }
    }
}
