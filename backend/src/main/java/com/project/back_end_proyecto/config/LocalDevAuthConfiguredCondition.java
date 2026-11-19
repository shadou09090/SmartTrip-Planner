package com.project.back_end_proyecto.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

public final class LocalDevAuthConfiguredCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return StringUtils.hasText(context.getEnvironment().getProperty("smarttrip.jwt.secret"))
                && StringUtils.hasText(context.getEnvironment().getProperty("smarttrip.local-auth.bootstrap-token"));
    }
}
