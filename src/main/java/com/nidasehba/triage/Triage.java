package com.nidasehba.triage;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/** The classification for one failure group; also the structured-output schema sent to the model. */
public record Triage(
        @JsonPropertyDescription("One of: PRODUCT_BUG, TEST_BUG, FLAKY, ENVIRONMENT, TEST_DATA")
        String category,
        @JsonPropertyDescription("0.0 to 1.0: how sure you are, given only the evidence shown")
        double confidence,
        @JsonPropertyDescription("Most likely root cause in one sentence")
        String rootCause,
        @JsonPropertyDescription("Exact lines or fragments copied from the failure text that support the category")
        List<String> evidence,
        @JsonPropertyDescription("The single most useful next step for the engineer, e.g. raise a bug for team X")
        String nextStep) {

    public static final List<String> CATEGORIES = List.of("PRODUCT_BUG", "TEST_BUG", "FLAKY", "ENVIRONMENT", "TEST_DATA");
}
