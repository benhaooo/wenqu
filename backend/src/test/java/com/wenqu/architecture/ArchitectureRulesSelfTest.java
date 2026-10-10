package com.wenqu.architecture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 用 fixture 包中故意违规的代码证明每条架构规则确实能发现违规。 */
class ArchitectureRulesSelfTest {

    private static final String FIXTURE = "com.wenqu.architecture.fixture";

    private static final JavaClasses CLASSES = new ClassFileImporter().importPackages(FIXTURE);

    private final ArchitectureRules rules = new ArchitectureRules(FIXTURE, List.of("document", "chat"));

    @Test
    void detectsAccessToAnotherModulesRepository() {
        assertViolation(rules.moduleBoundaries(), "DocumentServiceUsingChatRepository");
    }

    @Test
    void detectsControllerAccessingRepository() {
        assertViolation(rules.layering(), "DocumentControllerUsingRepository");
    }

    @Test
    void detectsCycleBetweenModules() {
        assertViolation(rules.noCycles(), "Cycle detected");
    }

    @Test
    void detectsAccessToAnotherModulesTable() {
        assertViolation(rules.tableOwnership(), "DocumentRepositoryUsingChatTable");
    }

    @Test
    void detectsApiExposingDomain() {
        assertViolation(rules.apiDoesNotExposeInternals(), "DocumentApiExposingDomain");
    }

    @Test
    void detectsDomainDependingOnSpring() {
        assertViolation(rules.domainIsFrameworkFree(), "DocumentDomainUsingSpring");
    }

    private static void assertViolation(ArchRule rule, String expected) {
        assertThatThrownBy(() -> rule.check(CLASSES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(expected);
    }
}
