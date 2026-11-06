package com.aegis.common.dto;

/** The outcome of running an approved action. */
public record ExecutionResult(boolean success, Object data, String error) {

    public static ExecutionResult success(Object data) {
        return new ExecutionResult(true, data, null);
    }

    public static ExecutionResult failure(String error) {
        return new ExecutionResult(false, null, error);
    }
}
