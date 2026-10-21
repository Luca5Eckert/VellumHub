package com.vellumhub.gateway_service.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * AFF-STR-001: domain code must not import the delivery or infrastructure layers.
 * Module/package layouts vary across services; an empty domain is reported as
 * uncovered rather than silently counted as architectural fitness evidence.
 */
class GatewayArchitectureFitnessTest {
    private static final String ROOT = "com.vellumhub.gateway_service";

    @Test
    void domainMustNotDependOnAdaptersOrControllers() {
        JavaClasses production = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(ROOT);
        long domainCount = production.stream()
                .filter(c -> c.getPackageName().contains(".domain."))
                .count();
        if (domainCount == 0) {
            // Services without a domain package cannot claim STR-001 coverage.
            org.junit.jupiter.api.Assumptions.assumeTrue(false,
                    "AFF-STR-001 not applicable: no domain packages in " + ROOT);
        }
        ArchRule rule = noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..infrastructure..", "..controller..", "..adapter..")
                .allowEmptyShould(false);
        rule.check(production);
    }
}
