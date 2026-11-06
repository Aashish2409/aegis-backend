package com.aegis.common.dto;

/** What the Policy Engine receives: the proposed action and who asked for it. */
public record PolicyRequest(AgentAction action, UserContext user) {
}
