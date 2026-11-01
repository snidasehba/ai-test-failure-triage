package com.nidasehba.triage;

import com.nidasehba.triage.FailureGrouper.FailureGroup;

import java.util.List;
import java.util.Locale;

/**
 * Rule-based fallback used when no API key is configured, and the baseline the AI classifier is compared
 * against. Rules are ordered from most to least specific.
 */
public class HeuristicClassifier implements Classifier {

    @Override
    public Triage classify(FailureGroup group) {
        TestFailure f = group.sample();
        String text = f.evidenceText();
        String lower = text.toLowerCase(Locale.ROOT);
        String type = f.type() == null ? "" : f.type();

        if (type.contains("Timeout") || lower.contains("connection refused") || lower.contains("connect timed out")
                || lower.contains("unknownhost")) {
            return triage("ENVIRONMENT", 0.8, "A dependency or test environment was unreachable", text,
                    "timed out", "refused", "Connect to");
        }
        if (type.contains("StaleElementReference")) {
            return triage("FLAKY", 0.7, "The page re-rendered between finding and using an element", text,
                    "stale element");
        }
        if (type.contains("NoSuchElement")) {
            return triage("TEST_BUG", 0.6, "A locator no longer matches the page, possibly after a UI change", text,
                    "Unable to locate element");
        }
        if (lower.contains("not found") && lower.matches("(?s).*\\b404\\b.*")) {
            return triage("TEST_DATA", 0.6, "The test depends on data that does not exist in this environment", text,
                    "not found");
        }
        if (lower.matches("(?s).*but was <5\\d\\d>.*") || lower.contains("internal_error") || lower.contains("exception in")) {
            return triage("PRODUCT_BUG", 0.75, "The service returned a server error", text, "but was <5", "INTERNAL_ERROR");
        }
        return new Triage("PRODUCT_BUG", 0.3, "Assertion failed with no clearer signal",
                List.of(firstLine(text)), "Investigate manually");
    }

    private static Triage triage(String category, double confidence, String rootCause, String text, String... cues) {
        List<String> evidence = text.lines()
                .filter(line -> java.util.Arrays.stream(cues).anyMatch(line::contains))
                .map(String::strip)
                .distinct()
                .limit(2)
                .toList();
        return new Triage(category, confidence, rootCause, evidence.isEmpty() ? List.of(firstLine(text)) : evidence,
                nextStepFor(category));
    }

    static String nextStepFor(String category) {
        return switch (category) {
            case "ENVIRONMENT" -> "Check the environment health and re-run; do not raise a product bug yet";
            case "FLAKY" -> "Re-run to confirm, then add an explicit wait or re-locate the element";
            case "TEST_BUG" -> "Compare the locator with the current page and update the page object";
            case "TEST_DATA" -> "Create the data in test setup instead of relying on existing records";
            default -> "Raise a bug with the service owner, attaching the response body";
        };
    }

    private static String firstLine(String text) {
        return text.strip().lines().findFirst().orElse("").strip();
    }
}
