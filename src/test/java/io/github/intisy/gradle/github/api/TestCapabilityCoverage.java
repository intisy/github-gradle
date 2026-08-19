package io.github.intisy.gradle.github.api;

import io.github.intisy.gradle.github.api.capability.Credentials;
import io.github.intisy.gradle.github.api.capability.JarResolver;
import io.github.intisy.gradle.github.api.capability.Publishing;
import io.github.intisy.gradle.github.api.capability.Releases;
import io.github.intisy.gradle.github.api.capability.Repositories;
import io.github.intisy.gradle.github.api.capability.SourceBuilds;
import io.github.intisy.gradle.github.api.model.ResolutionRequest;
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

    /**
     * Walks every public-facing {@code api} type, not just the five capability interfaces:
     * {@link JarResolver} and {@link ResolutionRequest} carry no implementation-detail types on
     * their own signatures either, and belong in the same coverage as {@link Credentials},
     * {@link Repositories}, {@link Releases}, {@link Publishing} and {@link SourceBuilds}.
     */
    @Test
    public void noApiMethodMentionsAnImplementationDetailType() {
        for (Class<?> apiType : Arrays.asList(Credentials.class, Repositories.class, Releases.class, Publishing.class,
                SourceBuilds.class, JarResolver.class, ResolutionRequest.class)) {
            for (Method method : apiType.getDeclaredMethods()) {
                assertNoForbiddenType(apiType, method, method.getGenericReturnType());
                for (Type parameterType : method.getGenericParameterTypes()) {
                    assertNoForbiddenType(apiType, method, parameterType);
                }
                for (Class<?> exceptionType : method.getExceptionTypes()) {
                    assertNoForbiddenType(apiType, method, exceptionType);
                }
            }
        }
    }

    private static void assertNoForbiddenType(Class<?> apiType, Method method, Type type) {
        if (type instanceof Class<?>) {
            Class<?> clazz = (Class<?>) type;
            if (clazz.isArray()) {
                assertNoForbiddenType(apiType, method, clazz.getComponentType());
                return;
            }
            String packageName = clazz.getPackage() != null ? clazz.getPackage().getName() : "";
            for (String forbidden : FORBIDDEN_PACKAGES) {
                assertTrue(!packageName.startsWith(forbidden),
                        apiType.getSimpleName() + "#" + method.getName() + " mentions " + clazz.getName()
                                + ", which belongs to the implementation-only package '" + forbidden + "'.");
            }
        } else if (type instanceof ParameterizedType) {
            ParameterizedType parameterized = (ParameterizedType) type;
            assertNoForbiddenType(apiType, method, parameterized.getRawType());
            for (Type argument : parameterized.getActualTypeArguments()) {
                assertNoForbiddenType(apiType, method, argument);
            }
        } else if (type instanceof GenericArrayType) {
            assertNoForbiddenType(apiType, method, ((GenericArrayType) type).getGenericComponentType());
        }
    }
}
