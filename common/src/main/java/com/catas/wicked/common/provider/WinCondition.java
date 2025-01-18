package com.catas.wicked.common.provider;

import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.context.condition.OperatingSystem;

public class WinCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context) {
        return OperatingSystem.getCurrent().isWindows();
    }
}
