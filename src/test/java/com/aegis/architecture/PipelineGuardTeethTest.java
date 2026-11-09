package com.aegis.architecture;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.aegis.common.api.Agent;
import com.aegis.common.api.ToolExecutor;
import com.aegis.common.dto.AgentAction;
import com.aegis.common.dto.AgentRequest;
import com.aegis.common.dto.ExecutionResult;
import com.aegis.common.enums.AgentType;
import com.aegis.database.DatabaseToolExecutor;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

/**
 * Proves that the guard rules have "teeth": each rule must FAIL when it meets
 * deliberately bad code. Without this, a rule with a typo could match nothing
 * and pass forever while protecting nothing.
 *
 * The bad classes below exist only for this test. They are never used by the app.
 */
class PipelineGuardTeethTest {

    // ----- deliberately bad example classes (they live in the test package, outside "pipeline") -----

    /** Breaks rule 1: calls execute() through the ToolExecutor interface. */
    static class SneakyCallerUsingInterface {
        void sneak(ToolExecutor executor, AgentAction action) {
            executor.execute(action);
        }
    }

    /** Breaks rule 1: calls execute() on a CONCRETE executor class. */
    static class SneakyCallerUsingConcreteClass {
        ExecutionResult sneak(DatabaseToolExecutor executor, AgentAction action) {
            return executor.execute(action);
        }
    }

    /** Breaks rule 2: an Agent that holds a ToolExecutor. */
    static class AgentThatHoldsAnExecutor implements Agent {
        @SuppressWarnings("unused")
        private ToolExecutor executor; // the dependency the rule must catch

        @Override
        public String getAgentId() {
            return "BAD_AGENT";
        }

        @Override
        public String getAgentName() {
            return "Bad Agent";
        }

        @Override
        public AgentType getType() {
            return AgentType.DATABASE;
        }

        @Override
        public AgentAction propose(AgentRequest request) {
            return null;
        }
    }

    // ----- the checks -----

    @Test
    void rule1_failsWhenCallingExecuteThroughTheInterface() {
        JavaClasses bad = new ClassFileImporter().importClasses(SneakyCallerUsingInterface.class);
        assertThrows(AssertionError.class,
                () -> PipelineGuardRules.onlyThePipelineMayCallExecute().check(bad));
    }

    @Test
    void rule1_failsWhenCallingExecuteOnAConcreteExecutor() {
        JavaClasses bad = new ClassFileImporter().importClasses(SneakyCallerUsingConcreteClass.class);
        assertThrows(AssertionError.class,
                () -> PipelineGuardRules.onlyThePipelineMayCallExecute().check(bad));
    }

    @Test
    void rule2_failsWhenAnAgentDependsOnAnExecutor() {
        JavaClasses bad = new ClassFileImporter().importClasses(AgentThatHoldsAnExecutor.class);
        assertThrows(AssertionError.class,
                () -> PipelineGuardRules.agentsMustNotDependOnExecutors().check(bad));
    }
}
