package com.oep.corelms.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit tests enforcing architectural boundaries and Clean Architecture principles
 * as required by the 8-Week Plan (L1 Governance: >= 6 ArchUnit rules in CI).
 */
@AnalyzeClasses(packages = "com.oep.corelms", importOptions = {ImportOption.DoNotIncludeTests.class})
public class ArchitectureBoundaryTest {

    // ── Rule 1: Controllers must not directly access Repositories ───────────
    @ArchTest
    static final ArchRule controllers_must_not_access_repositories_directly =
            noClasses().that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat().resideInAPackage("..repository..")
                    .because("Controllers must only orchestrate requests through Services, not directly query Repositories");

    // ── Rule 2: Repositories must not depend on Services or Controllers ──────
    @ArchTest
    static final ArchRule repositories_must_not_depend_on_services_or_controllers =
            noClasses().that().resideInAPackage("..repository..")
                    .should().dependOnClassesThat().resideInAnyPackage("..service..", "..controller..")
                    .because("Data access layer (Repositories) must be independent of business logic and presentation layers");

    // ── Rule 3: Concrete Services must be annotated with @Service ────────────
    @ArchTest
    static final ArchRule services_must_be_annotated_with_service =
            classes().that().resideInAPackage("..service..")
                    .and().haveSimpleNameEndingWith("Service")
                    .and().areNotInterfaces()
                    .should().beAnnotatedWith(Service.class)
                    .because("Service implementations must be registered as Spring service beans");

    // ── Rule 4: Controllers must be annotated with @RestController ───────────
    @ArchTest
    static final ArchRule controllers_must_be_annotated_with_rest_controller =
            classes().that().resideInAPackage("..controller..")
                    .and().haveSimpleNameEndingWith("Controller")
                    .should().beAnnotatedWith(RestController.class)
                    .because("REST API controllers must be annotated with @RestController");

    // ── Rule 5: Repositories must reside in repository package ───────────────
    @ArchTest
    static final ArchRule repositories_must_reside_in_repository_package =
            classes().that().areAssignableTo(Repository.class)
                    .should().resideInAPackage("..repository..")
                    .because("All Spring Data Repositories must reside in the dedicated repository package");

    // ── Rule 6: Services must not depend on Controllers ──────────────────────
    @ArchTest
    static final ArchRule services_must_not_depend_on_controllers =
            noClasses().that().resideInAPackage("..service..")
                    .should().dependOnClassesThat().resideInAPackage("..controller..")
                    .because("Service layer must not have reverse dependencies on presentation controllers");
}
