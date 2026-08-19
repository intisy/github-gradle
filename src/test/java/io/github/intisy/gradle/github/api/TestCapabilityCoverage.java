package io.github.intisy.gradle.github.api;

import io.github.intisy.gradle.github.impl.GitHub;
import org.junit.jupiter.api.Test;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
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
        for (Class<?> capability : Arrays.asList(Credentials.class, Repositories.class, Releases.class, Publishing.class, SourceBuilds.class)) {
            for (Method method : capability.getDeclaredMethods()) {
                assertNoForbiddenType(capability, method, method.getGenericReturnType());
                for (Type parameterType : method.getGenericParameterTypes()) {
                    assertNoForbiddenType(capability, method, parameterType);
                }
                for (Class<?> exceptionType : method.getExceptionTypes()) {
                    assertNoForbiddenType(capability, method, exceptionType);
                }
            }
        }
    }

    private static void assertNoForbiddenType(Class<?> capability, Method method, Type type) {
        if (type instanceof Class<?>) {
            Class<?> clazz = (Class<?>) type;
            if (clazz.isArray()) {
                assertNoForbiddenType(capability, method, clazz.getComponentType());
                return;
            }
            String packageName = clazz.getPackage() != null ? clazz.getPackage().getName() : "";
            for (String forbidden : FORBIDDEN_PACKAGES) {
                assertTrue(!packageName.startsWith(forbidden),
                        capability.getSimpleName() + "#" + method.getName() + " mentions " + clazz.getName()
                                + ", which belongs to the implementation-only package '" + forbidden + "'.");
            }
        } else if (type instanceof ParameterizedType) {
            ParameterizedType parameterized = (ParameterizedType) type;
            assertNoForbiddenType(capability, method, parameterized.getRawType());
            for (Type argument : parameterized.getActualTypeArguments()) {
                assertNoForbiddenType(capability, method, argument);
            }
        } else if (type instanceof GenericArrayType) {
            assertNoForbiddenType(capability, method, ((GenericArrayType) type).getGenericComponentType());
        }
    }
}
