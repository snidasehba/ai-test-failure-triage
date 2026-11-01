package com.nidasehba.triage;

import com.nidasehba.triage.FailureGrouper.FailureGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * Guardrails on the classifier's answer. A triage is only trusted when its category is valid, its
 * confidence is high enough, and every piece of evidence is actually present in the failure text;
 * otherwise it is flagged for a human instead of being reported as fact.
 */
public final class TriageValidator {

    public static final double MIN_CONFIDENCE = 0.6;

    public record Result(Triage triage, boolean needsHuman, List<String> reasons) {
    }

    private TriageValidator() {
    }

    public static Result validate(FailureGroup group, Triage t) {
        List<String> reasons = new ArrayList<>();
        if (t.category() == null || !Triage.CATEGORIES.contains(t.category())) {
            reasons.add("unknown category '" + t.category() + "'");
        }
        if (t.confidence() < MIN_CONFIDENCE) {
            reasons.add(String.format("low confidence (%.2f)", t.confidence()));
        }
        if (t.evidence() == null || t.evidence().isEmpty()) {
            reasons.add("no evidence quoted");
        } else {
            String allText = group.failures().stream().map(TestFailure::evidenceText).reduce("", String::concat);
            for (String e : t.evidence()) {
                if (!allText.contains(e.strip())) {
                    reasons.add("evidence not found in the failure text: \"" + e + "\"");
                }
            }
        }
        return new Result(t, !reasons.isEmpty(), reasons);
    }
}
