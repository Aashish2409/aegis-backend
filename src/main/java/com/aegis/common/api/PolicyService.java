package com.aegis.common.api;

import com.aegis.common.dto.PolicyRequest;
import com.aegis.common.dto.PolicyResponse;

/** The Policy Engine's public entry point. */
public interface PolicyService {

    PolicyResponse evaluate(PolicyRequest request);
}
