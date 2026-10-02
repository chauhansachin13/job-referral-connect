package com.referralconnect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A dependency-free test runner: finds {@link Test} methods on the listed classes, runs each on
 * a fresh instance, and exits non-zero if anything fails.
 *
 * <pre>
 *   javac -d out-test --source-path src/main/java:src/test/java src/test/java/com/referralconnect/TestRunner.java
 *   java -cp out-test com.referralconnect.TestRunner
 * </pre>
 */
public final class TestRunner {

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Test {
    }

    private static final List<Class<?>> SUITES = List.of(
            JsonTest.class,
            RoleClassifierTest.class,
            IndiaLocationsTest.class,
            AtsParsersTest.class,
            BoardUrlParserTest.class,
            JobScannerTest.class,
            SourcesTest.class,
            JobServiceTest.class,
            PasswordHasherTest.class,
            DataStoreTest.class,
            AuthServiceTest.class,
            ReferralServiceTest.class);

    public static void main(String[] args) throws Exception {
        int passed = 0;
        List<String> failures = new ArrayList<>();
        for (Class<?> suite : SUITES) {
            Method[] methods = suite.getDeclaredMethods();
            Arrays.sort(methods, Comparator.comparing(Method::getName));
            for (Method m : methods) {
                if (!m.isAnnotationPresent(Test.class)) {
                    continue;
                }
                String name = suite.getSimpleName() + "." + m.getName();
                try {
                    Object instance = suite.getDeclaredConstructor().newInstance();
                    m.setAccessible(true);
                    m.invoke(instance);
                    passed++;
                    System.out.println("  PASS  " + name);
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause();
                    failures.add(name + ": " + cause);
                    System.out.println("  FAIL  " + name + "\n        " + cause);
                }
            }
        }
        System.out.println();
        System.out.println(passed + " passed, " + failures.size() + " failed");
        if (!failures.isEmpty()) {
            System.exit(1);
        }
    }

    // ---------------------------------------------------------------- assertions

    public static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
        }
    }

    public static void equal(Object expected, Object actual, String context) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(context + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    /** Asserts that the action throws {@code type}; returns the exception for message checks. */
    public static <T extends Throwable> T fails(Class<T> type, ThrowingRunnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
            throw new AssertionError("expected " + type.getSimpleName() + " but got " + t, t);
        }
        throw new AssertionError("expected " + type.getSimpleName() + " but nothing was thrown");
    }

    private TestRunner() {
    }
}
