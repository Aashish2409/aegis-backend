package com.aegis.policy;

import com.aegis.common.dto.AgentAction;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Scans proposals and action parameters for known prompt injection patterns
 * and adversarial instructions.
 *
 * Note: Heuristic pattern scanning is an early detection layer. Deterministic
 * Java permission allow-lists in PolicyEngine provide the definitive security boundary.
 */
@Component
public class PromptInjectionScanner {

    private static final List<String> SUSPICIOUS_PATTERNS = List.of(
            "ignore previous instructions",
            "ignore all previous instructions",
            "ignore all instructions",
            "disregard previous prompts",
            "disregard all previous",
            "system override",
            "developer mode enabled",
            "you are now in developer mode",
            "dan mode",
            "jailbreak",
            "bypass security",
            "disable security filters",
            "reveal system prompt",
            "print system prompt"
    );

    public record ScanResult(boolean flagged, String matchedPattern, String detail) {
        public static ScanResult clean() {
            return new ScanResult(false, null, null);
        }

        public static ScanResult detected(String pattern, String detail) {
            return new ScanResult(true, pattern, detail);
        }
    }

    /**
     * Scans all textual fields of an AgentAction (operation, resource, and parameter values).
     */
    public ScanResult scan(AgentAction action) {
        if (action == null) {
            return ScanResult.clean();
        }

        ScanResult opScan = scanText("operation", action.operation());
        if (opScan.flagged()) {
            return opScan;
        }

        ScanResult resScan = scanText("resource", action.resource());
        if (resScan.flagged()) {
            return resScan;
        }

        if (action.parameters() != null) {
            for (Map.Entry<String, Object> entry : action.parameters().entrySet()) {
                String valStr = entry.getValue() == null ? "" : entry.getValue().toString();
                ScanResult paramScan = scanText("parameter:" + entry.getKey(), valStr);
                if (paramScan.flagged()) {
                    return paramScan;
                }
            }
        }

        return ScanResult.clean();
    }

    public ScanResult scanText(String fieldName, String text) {
        if (text == null || text.isBlank()) {
            return ScanResult.clean();
        }

        String normalized = text.toLowerCase(Locale.ROOT);
        for (String pattern : SUSPICIOUS_PATTERNS) {
            if (normalized.contains(pattern)) {
                return ScanResult.detected(pattern, "Suspicious pattern '" + pattern + "' found in " + fieldName);
            }
        }

        return ScanResult.clean();
    }
}
