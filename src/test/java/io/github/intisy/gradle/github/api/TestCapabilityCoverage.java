package io.github.intisy.gradle.github.api;

import io.github.intisy.gradle.github.impl.GitHub;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestCapabilityCoverage {
    private static final List<String> FORBIDDEN_PACKAGES = Arrays.asList(
            "com.google.gson",
            "org.gradle",
            "org.eclipse.jgit"
    );

    @Test
    public void gitHubImplementsAllFourCapabilityInterfaces() {
        assertTrue(Credentials.class.isAssignableFrom(GitHub.class), "GitHub must implement Credentials");
        assertTrue(Repositories.class.isAssignableFrom(GitHub.class), "GitHub must implement Repositories");
        assertTrue(Releases.class.isAssignableFrom(GitHub.class), "GitHub must implement Releases");
        assertTrue(Publishing.class.isAssignableFrom(GitHub.class), "GitHub must implement Publishing");
    }

    @Test
    public void noCapabilityMethodMentionsAnImplementationDetailType() {
        for (Class<?> capability : Arrays.asList(Credentials.class, Repositories.class, Releases.class, Publishing.class)) {
            for (Method method : capability.getDeclaredMethods()) {
                assertNoForbiddenType(capability, method, method.getReturnType());
                for (Class<?> parameterType : method.getParameterTypes()) {
                    assertNoForbiddenType(capability, method, parameterType);
                }
            }
        }
    }

    private static void assertNoForbiddenType(Class<?> capability, Method method, Class<?> type) {
        String packageName = type.getPackage() != null ? type.getPackage().getName() : "";
        for (String forbidden : FORBIDDEN_PACKAGES) {
            assertTrue(!packageName.startsWith(forbidden),
                    capability.getSimpleName() + "#" + method.getName() + " mentions " + type.getName()
                            + ", which belongs to the implementation-only package '" + forbidden + "'.");
        }
    }
}
