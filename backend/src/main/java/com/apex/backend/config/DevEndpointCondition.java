package com.apex.backend.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Profiles;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class DevEndpointCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        boolean flagEnabled = Boolean.TRUE.equals(
                context.getEnvironment().getProperty("apex.dev.enabled", Boolean.class, false));
        boolean legacyEnabled = Boolean.TRUE.equals(
                context.getEnvironment().getProperty("apex.dev.endpoints", Boolean.class, false));
        boolean devProfile = context.getEnvironment().acceptsProfiles(Profiles.of("dev"));
        return flagEnabled || legacyEnabled || devProfile;
    }
}
