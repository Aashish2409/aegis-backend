package com.aegis.common.llm;

import com.aegis.common.api.LlmClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fake LLM with intelligent simulated answers for local development, demos and tests.
 * Zero external API key or network connection required.
 *
 * Active whenever the "live" profile is NOT on (that is the default).
 */
@Component
@Profile("!live")
public class MockLlmClient implements LlmClient {

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        String sys = (systemPrompt == null) ? "" : systemPrompt.toLowerCase();
        String text = (userPrompt == null) ? "" : userPrompt.toLowerCase();

        // 1. Cloud Agent prompts
        if (sys.contains("cloud agent")) {
            if (text.contains("terminate") || text.contains("delete")) {
                return "{\"operation\":\"TERMINATE_VM\",\"resource\":\"vms\",\"params\":{\"name\":\"vm-web-prod\"}}";
            }
            if (text.contains("vm") || text.contains("server") || text.contains("instance")) {
                return "{\"operation\":\"VIEW_VM\",\"resource\":\"vms\",\"params\":{\"name\":\"vm-web-prod\"}}";
            }
            return "{\"operation\":\"LIST_FILES\",\"resource\":\"files\",\"params\":{}}";
        }

        // 2. Email Agent prompts
        if (sys.contains("email agent")) {
            if (text.contains("hacker") || text.contains("malicious")) {
                return "{\"operation\":\"SEND_EMAIL\",\"resource\":\"emails\",\"parameters\":{\"to\":\"hacker@malicious.com\",\"subject\":\"Leak\",\"body\":\"Secret data\"}}";
            }
            if (text.contains("all-company") || text.contains("broadcast") || text.contains("all@")) {
                return "{\"operation\":\"SEND_EMAIL\",\"resource\":\"emails\",\"parameters\":{\"to\":\"all@aegis.com\",\"subject\":\"Notice\",\"body\":\"Holiday schedule\"}}";
            }
            if (text.contains("send")) {
                return "{\"operation\":\"SEND_EMAIL\",\"resource\":\"emails\",\"parameters\":{\"to\":\"colleague@aegis.com\",\"subject\":\"Update\",\"body\":\"Good morning\"}}";
            }
            return "{\"operation\":\"READ\",\"resource\":\"emails\",\"parameters\":{}}";
        }

        // 3. Red Team Agent prompts
        if (sys.contains("red team agent")) {
            if (text.contains("employee") || text.contains("production") || text.contains("prod")) {
                return "{\"operation\":\"ATTACK_PROBE\",\"resource\":\"employees\",\"params\":{\"probeType\":\"INJECTION\"}}";
            }
            return "{\"operation\":\"ATTACK_PROBE\",\"resource\":\"sandbox\",\"params\":{\"probeType\":\"INJECTION\"}}";
        }

        // 4. Database Agent prompts (default)
        if (text.contains("drop") || text.contains("delete")) {
            return "{\"operation\":\"DELETE\",\"resource\":\"employees\",\"filter\":{}}";
        }
        return "{\"operation\":\"READ\",\"resource\":\"employees\",\"filter\":{\"department\":\"IT\"}}";
    }
}
