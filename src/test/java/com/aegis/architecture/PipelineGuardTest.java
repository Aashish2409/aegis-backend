package com.aegis.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Runs the pipeline guard rules against our real production code.
 * If this test fails, someone wrote code that could run an action without
 * going through SecurityPipeline. The failure message names the class and line.
 */
class PipelineGuardTest {

    private static JavaClasses productionClasses;

    @BeforeAll
    static void importProductionClasses() {
        // DoNotIncludeTests: look only at src/main code. Our own unit tests are
        // allowed to call executors directly (for example DatabaseToolExecutorTest).
        productionClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.aegis");
    }

    /** Safety net: if the package name were wrong, the rules would check nothing and pass silently. */
    @Test
    void productionClassesWereImported() {
        assertFalse(productionClasses.isEmpty(), "No classes found under com.aegis, check the package name");
    }

    @Test
    void onlyThePipelineCallsExecute() {
        PipelineGuardRules.onlyThePipelineMayCallExecute().check(productionClasses);
    }

    @Test
    void agentsNeverDependOnExecutors() {
        PipelineGuardRules.agentsMustNotDependOnExecutors().check(productionClasses);
    }
}
