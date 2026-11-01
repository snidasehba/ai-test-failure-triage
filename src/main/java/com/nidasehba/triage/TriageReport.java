package com.nidasehba.triage;

import com.nidasehba.triage.FailureGrouper.FailureGroup;
import com.nidasehba.triage.TriageValidator.Result;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Markdown report: totals per category first, then one section per failure group. */
public final class TriageReport {

    public record Item(FailureGroup group, Result result) {
    }

    private TriageReport() {
    }

    public static String markdown(List<Item> items, String classifierName) {
        int totalFailures = items.stream().mapToInt(i -> i.group().failures().size()).sum();
        StringBuilder sb = new StringBuilder("# Test failure triage\n\n");
        sb.append(String.format("%d failed tests in %d groups. Classifier: %s.%n%n", totalFailures, items.size(),
                classifierName));

        Map<String, Integer> byCategory = new TreeMap<>();
        items.forEach(i -> byCategory.merge(label(i), i.group().failures().size(), Integer::sum));
        sb.append("| Category | Failed tests |\n|---|---|\n");
        byCategory.forEach((cat, n) -> sb.append("| ").append(cat).append(" | ").append(n).append(" |\n"));

        int n = 1;
        for (Item i : items) {
            Triage t = i.result().triage();
            sb.append(String.format("%n## %d. %s (%d tests)%n%n", n++, label(i), i.group().failures().size()));
            sb.append("**Signature:** `").append(i.group().signature()).append("`\n\n");
            sb.append("**Likely cause:** ").append(t.rootCause()).append("\n\n");
            sb.append(String.format("**Confidence:** %.2f%n%n", t.confidence()));
            sb.append("**Next step:** ").append(t.nextStep()).append("\n\n");
            if (i.result().needsHuman()) {
                sb.append("**Needs a human:** ").append(String.join("; ", i.result().reasons())).append("\n\n");
            }
            sb.append("**Evidence:**\n");
            t.evidence().forEach(e -> sb.append("- `").append(e.replace("`", "'")).append("`\n"));
            sb.append("\n**Tests:**\n");
            i.group().failures().forEach(f -> sb.append("- ").append(f.fullName()).append('\n'));
        }
        return sb.toString();
    }

    private static String label(Item i) {
        return i.result().needsHuman() ? "NEEDS_HUMAN" : i.result().triage().category();
    }
}
