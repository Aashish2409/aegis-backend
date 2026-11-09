package com.aegis.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.aegis.common.api.Agent;
import com.aegis.common.api.ToolExecutor;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * The architecture rules that protect Aegis's one big promise:
 * "nothing runs unless the pipeline (and therefore the Policy Engine) said yes".
 *
 * The rules live here, in one place, and are used by two test classes:
 *   - PipelineGuardTest      checks the rules against our REAL production code
 *   - PipelineGuardTeethTest checks that the rules really FAIL on bad code
 *                            (a rule that can never fail protects nothing)
 *
 * Owner: Aashish. If a rule fails, do NOT weaken the rule. Fix the code that
 * breaks it, or talk to Aashish.
 */
final class PipelineGuardRules {

    /** The only package allowed to run approved actions. ".." means "and all sub-packages". */
    static final String PIPELINE_PACKAGE = "com.aegis.pipeline..";

    private PipelineGuardRules() {
        // utility class, no instances
    }

    /**
     * RULE 1: only classes in the pipeline package may call execute() on a ToolExecutor.
     *
     * Why: execute() is the moment an action really happens. It must only be
     * reached after the Policy Engine said ALLOW (or a reviewer approved).
     * If a controller, an agent or a helper could call it directly, they would
     * skip the policy check and the audit log.
     *
     * Details:
     *  - It also catches calls made on a CONCRETE executor, for example
     *    databaseToolExecutor.execute(...), not only calls on the interface.
     *  - Executor classes themselves are excluded, so an executor may use its own
     *    internal helper methods.
     *  - Test code is excluded by the importer (see PipelineGuardTest), because
     *    executor unit tests must be allowed to call execute() directly.
     *
     * Known limit: it checks direct method calls. It does not follow method
     * references such as executor::execute. Code review has to catch those.
     */
    static ArchRule onlyThePipelineMayCallExecute() {
        return classes()
                .that().resideOutsideOfPackage(PIPELINE_PACKAGE)
                .and().areNotAssignableTo(ToolExecutor.class)
                .should(notCallExecuteOnAnExecutor())
                .because("only SecurityPipeline may run actions, after the Policy Engine decided");
    }

    /**
     * RULE 2: an Agent must never depend on a ToolExecutor.
     *
     * Why: agents only PROPOSE actions. If an agent could even reference an
     * executor, a tricked LLM plus one careless line of code could run
     * something without any policy check.
     */
    static ArchRule agentsMustNotDependOnExecutors() {
        return noClasses()
                .that().areAssignableTo(Agent.class)
                .should().dependOnClassesThat().areAssignableTo(ToolExecutor.class)
                .because("agents only propose actions; they must have no way to run them");
    }

    /**
     * Custom condition used by rule 1: "this class has no call to execute() whose
     * target class is a ToolExecutor (the interface or any implementation)".
     */
    private static ArchCondition<JavaClass> notCallExecuteOnAnExecutor() {
        return new ArchCondition<>("not call execute() on a ToolExecutor") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (JavaMethodCall call : javaClass.getMethodCallsFromSelf()) {
                    boolean isExecute = call.getTarget().getName().equals("execute");
                    boolean onAnExecutor = call.getTarget().getOwner().isAssignableTo(ToolExecutor.class);
                    if (isExecute && onAnExecutor) {
                        // This adds one violation; the message shows the class and line number.
                        events.add(SimpleConditionEvent.violated(call, call.getDescription()));
                    }
                }
            }
        };
    }
}
