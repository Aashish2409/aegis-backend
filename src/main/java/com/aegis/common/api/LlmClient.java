package com.aegis.common.api;

/**
 * The only way agents talk to an LLM. Two implementations exist:
 * MockLlmClient (default: tests, CI, demos) and a real Gemini client
 * (profile "live", added later).
 */
public interface LlmClient {

    String complete(String systemPrompt, String userPrompt);
}
