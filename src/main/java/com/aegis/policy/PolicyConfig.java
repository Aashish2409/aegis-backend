package com.aegis.policy;

import com.aegis.common.enums.AgentType;
import com.aegis.common.enums.UserRole;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * In-memory representation of loaded policy rules, role permissions, and allow-lists.
 */
public record PolicyConfig(
        Map<UserRole, Set<String>> allowedOperationsByRole,
        Map<AgentType, Set<String>> allowedOperationsByAgent,
        Set<String> reviewRequiredOperations,
        Set<String> globalBlockedOperations) {

    public PolicyConfig {
        allowedOperationsByRole = unmodifiableEnumMap(allowedOperationsByRole, UserRole.class);
        allowedOperationsByAgent = unmodifiableEnumMap(allowedOperationsByAgent, AgentType.class);
        reviewRequiredOperations = unmodifiableSet(reviewRequiredOperations);
        globalBlockedOperations = unmodifiableSet(globalBlockedOperations);
    }

    private static <K extends Enum<K>, V> Map<K, Set<V>> unmodifiableEnumMap(Map<K, Set<V>> source, Class<K> keyType) {
        Map<K, Set<V>> target = new EnumMap<>(keyType);
        if (source != null) {
            source.forEach((k, v) -> target.put(k, unmodifiableSet(v)));
        }
        return Collections.unmodifiableMap(target);
    }

    private static <T> Set<T> unmodifiableSet(Set<T> source) {
        return (source == null) ? Set.of() : Collections.unmodifiableSet(new HashSet<>(source));
    }
}
