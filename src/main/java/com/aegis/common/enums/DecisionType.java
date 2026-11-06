package com.aegis.common.enums;

/** The Policy Engine's verdict on a proposed action. */
public enum DecisionType {
    ALLOW,   // safe: the executor may run it
    BLOCK,   // forbidden: reject it
    REVIEW   // unsure: hold for a human to approve or reject
}
