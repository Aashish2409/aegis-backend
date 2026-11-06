package com.aegis.common.llm;

import com.aegis.common.api.LlmClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fake LLM with fixed answers. Used for tests, CI and demos, so nobody needs
 * an API key and nothing hits Gemini's free-tier limits.
 *
 * Active whenever the "live" profile is NOT on (that is the default).
 * It returns the structured JSON that real agents will parse:
 *   {"operation": "...", "resource": "...", "filter": {...}}
 */
@Component
@Profile("!live")
public class MockLlmClient implements LlmClient {

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        String text = (userPrompt == null) ? "" : userPrompt.toLowerCase();

        // Lets us demo a blocked action without a real LLM.
        if (text.contains("drop") || text.contains("delete")) {
            return "{\"operation\":\"DELETE\",\"resource\":\"employees\",\"filter\":{}}";
        }
        // Default: a harmless read.
        return "{\"operation\":\"READ\",\"resource\":\"employees\",\"filter\":{\"department\":\"IT\"}}";
    }
}
