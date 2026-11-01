package com.nidasehba.triage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups failures that share a root cause, so twenty tests broken by one bug become one item to triage.
 * The signature is the exception type plus the message with volatile parts (ids, numbers, durations)
 * replaced by placeholders.
 */
public final class FailureGrouper {

    public record FailureGroup(String signature, List<TestFailure> failures) {
        public TestFailure sample() {
            return failures.get(0);
        }
    }

    private FailureGrouper() {
    }

    public static List<FailureGroup> group(List<TestFailure> failures) {
        Map<String, List<TestFailure>> bySignature = new LinkedHashMap<>();
        for (TestFailure f : failures) {
            bySignature.computeIfAbsent(signature(f), k -> new ArrayList<>()).add(f);
        }
        List<FailureGroup> groups = new ArrayList<>();
        bySignature.forEach((sig, fs) -> groups.add(new FailureGroup(sig, fs)));
        groups.sort((a, b) -> Integer.compare(b.failures().size(), a.failures().size()));
        return groups;
    }

    static String signature(TestFailure f) {
        // The whole message, not just line one: REST Assured's first line ("1 expectation failed.") is generic.
        String msg = f.message() == null ? "" : f.message();
        String normalised = msg
                .replaceAll("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", "<uuid>")
                .replaceAll("\\b[a-z]+_[0-9a-z]{4,}\\b", "<id>")
                .replaceAll("\\(Session info:[^)]*\\)", "")
                .replaceAll("(?<!<)\\b\\d+(\\.\\d+)?\\b(?!>)", "<n>") // keeps status codes such as <500>
                .replaceAll("\\s+", " ")
                .strip();
        if (normalised.length() > 200) {
            normalised = normalised.substring(0, 200);
        }
        String type = f.type() == null ? "" : f.type().substring(f.type().lastIndexOf('.') + 1);
        return type + ": " + normalised;
    }
}
