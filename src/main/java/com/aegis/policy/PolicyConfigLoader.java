package com.aegis.policy;

import com.aegis.common.enums.AgentType;
import com.aegis.common.enums.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Loads baseline policy rules and allow-lists from an external YAML file (if present)
 * or built-in default YAML configuration at startup.
 */
@Component
public class PolicyConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(PolicyConfigLoader.class);

    private static final String DEFAULT_POLICIES_YAML = """
            roles:
              ADMIN:
                allowed_operations:
                  - READ
                  - SEARCH
                  - REPORT
                  - EXPORT
                  - UPDATE
                  - DELETE
                  - SEND_EMAIL
              REVIEWER:
                allowed_operations:
                  - READ
                  - SEARCH
                  - REPORT
                  - EXPORT
                  - SEND_EMAIL
              USER:
                allowed_operations:
                  - READ
                  - SEARCH
                  - REPORT

            agents:
              DATABASE:
                allowed_operations:
                  - READ
                  - SEARCH
                  - REPORT
              CLOUD:
                allowed_operations:
                  - READ
                  - SEARCH
                  - REPORT
              EMAIL:
                allowed_operations:
                  - READ
                  - SEND_EMAIL
              RED_TEAM:
                allowed_operations:
                  - READ
                  - ATTACK_PROBE

            review_required:
              - EXPORT
              - UPDATE
              - SEND_MASS_EMAIL

            blocked:
              - DROP
              - TRUNCATE
              - SHUTDOWN
              - DELETE_DATABASE
            """;

    private final ResourceLoader resourceLoader;
    private volatile PolicyConfig activeConfig;

    public PolicyConfigLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
        this.activeConfig = loadConfiguration();
    }

    public PolicyConfig getActiveConfig() {
        return activeConfig;
    }

    public PolicyConfig loadConfiguration() {
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Map<String, Object> data = null;

        try {
            Resource resource = resourceLoader.getResource("classpath:policies.yml");
            if (resource.exists()) {
                try (InputStream in = resource.getInputStream()) {
                    data = yaml.load(in);
                    log.info("Loaded custom policy configuration from classpath:policies.yml");
                }
            }
        } catch (Exception e) {
            log.warn("Could not load external policies.yml, falling back to embedded defaults: {}", e.getMessage());
        }

        if (data == null) {
            log.info("Initializing baseline policy configuration from embedded default YAML");
            data = yaml.load(DEFAULT_POLICIES_YAML);
        }

        return parseConfig(data);
    }

    @SuppressWarnings("unchecked")
    private PolicyConfig parseConfig(Map<String, Object> data) {
        Map<UserRole, Set<String>> roleMap = new EnumMap<>(UserRole.class);
        Map<AgentType, Set<String>> agentMap = new EnumMap<>(AgentType.class);
        Set<String> reviewSet = new HashSet<>();
        Set<String> blockedSet = new HashSet<>();

        if (data != null) {
            // Parse roles
            Object rolesObj = data.get("roles");
            if (rolesObj instanceof Map<?, ?> rolesData) {
                for (Map.Entry<?, ?> entry : rolesData.entrySet()) {
                    try {
                        UserRole role = UserRole.valueOf(entry.getKey().toString().trim().toUpperCase(Locale.ROOT));
                        if (entry.getValue() instanceof Map<?, ?> roleAttrs) {
                            Object ops = roleAttrs.get("allowed_operations");
                            if (ops instanceof List<?> opList) {
                                Set<String> opsSet = new HashSet<>();
                                for (Object op : opList) {
                                    opsSet.add(op.toString().trim().toUpperCase(Locale.ROOT));
                                }
                                roleMap.put(role, opsSet);
                            }
                        }
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }

            // Parse agents
            Object agentsObj = data.get("agents");
            if (agentsObj instanceof Map<?, ?> agentsData) {
                for (Map.Entry<?, ?> entry : agentsData.entrySet()) {
                    try {
                        AgentType agentType = AgentType.valueOf(entry.getKey().toString().trim().toUpperCase(Locale.ROOT));
                        if (entry.getValue() instanceof Map<?, ?> agentAttrs) {
                            Object ops = agentAttrs.get("allowed_operations");
                            if (ops instanceof List<?> opList) {
                                Set<String> opsSet = new HashSet<>();
                                for (Object op : opList) {
                                    opsSet.add(op.toString().trim().toUpperCase(Locale.ROOT));
                                }
                                agentMap.put(agentType, opsSet);
                            }
                        }
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }

            // Parse review_required
            Object reviewObj = data.get("review_required");
            if (reviewObj instanceof List<?> reviewList) {
                for (Object item : reviewList) {
                    reviewSet.add(item.toString().trim().toUpperCase(Locale.ROOT));
                }
            }

            // Parse blocked
            Object blockedObj = data.get("blocked");
            if (blockedObj instanceof List<?> blockedList) {
                for (Object item : blockedList) {
                    blockedSet.add(item.toString().trim().toUpperCase(Locale.ROOT));
                }
            }
        }

        return new PolicyConfig(roleMap, agentMap, reviewSet, blockedSet);
    }
}
