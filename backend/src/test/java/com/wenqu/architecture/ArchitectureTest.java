package com.wenqu.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

    static final List<String> BUSINESS_MODULES =
            List.of("auth", "workspace", "document", "ingestion", "retrieval", "chat", "usage");

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.wenqu");

    private final ArchitectureRules rules = new ArchitectureRules("com.wenqu", BUSINESS_MODULES);

    @Test
    void moduleBoundaries() {
        rules.moduleBoundaries().check(CLASSES);
    }

    @Test
    void layering() {
        rules.layering().check(CLASSES);
    }

    @Test
    void noCycles() {
        rules.noCycles().check(CLASSES);
    }

    @Test
    void tableOwnership() {
        rules.tableOwnership().check(CLASSES);
    }

    @Test
    void apiDoesNotExposeInternals() {
        rules.apiDoesNotExposeInternals().check(CLASSES);
    }

    @Test
    void domainIsFrameworkFree() {
        rules.domainIsFrameworkFree().check(CLASSES);
    }
}
