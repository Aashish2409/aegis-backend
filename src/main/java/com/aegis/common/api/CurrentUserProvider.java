package com.aegis.common.api;

import com.aegis.common.dto.UserContext;

/** Returns the VERIFIED caller (from the validated JWT), never from the request body. */
public interface CurrentUserProvider {

    UserContext getCurrentUser();
}