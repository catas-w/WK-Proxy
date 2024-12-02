package com.catas.wicked.common.util;

import io.micronaut.core.util.AntPathMatcher;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class AntMatcherUtils {

    private static final AntPathMatcher antPathMatcher = new AntPathMatcher();


    public static boolean matches(String pattern, String target) {
        if (!pattern.startsWith(WebUtils.HTTP_PREFIX) && !pattern.startsWith(WebUtils.HTTPS_PREFIX)) {
            target = WebUtils.removeProtocol(target);
        }
        return antPathMatcher.matches(pattern, target);
    }

    public static boolean matchAny(List<String> patterns, String target) {
        if (target == null || patterns == null || patterns.isEmpty()) {
            return false;
        }
        for (String pattern : patterns) {
            if (matches(pattern, target)) {
                return true;
            }
        }
        return false;
    }


}
