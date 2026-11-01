package com.nidasehba.triage;

import com.nidasehba.triage.FailureGrouper.FailureGroup;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Usage: java -jar ai-test-failure-triage.jar <surefire-reports-dir> [--heuristic]
 * Uses Claude when ANTHROPIC_API_KEY is set (unless --heuristic is given), otherwise the rule-based
 * classifier. Writes triage-report.md in the current directory.
 */
public final class App {

    private App() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java -jar ai-test-failure-triage.jar <reports-dir> [--heuristic]");
            System.exit(1);
        }
        boolean useClaude = !System.getenv().getOrDefault("ANTHROPIC_API_KEY", "").isBlank() && !List.of(args).contains("--heuristic");
        Classifier classifier = useClaude ? ClaudeClassifier.fromEnv() : new HeuristicClassifier();
        String report = run(Path.of(args[0]), classifier, useClaude ? "Claude" : "rule-based");
        Files.writeString(Path.of("triage-report.md"), report);
        System.out.println(report);
    }

    static String run(Path reportsDir, Classifier classifier, String classifierName) throws Exception {
        List<FailureGroup> groups = FailureGrouper.group(SurefireParser.parseDirectory(reportsDir));
        List<TriageReport.Item> items = new ArrayList<>();
        for (FailureGroup g : groups) {
            items.add(new TriageReport.Item(g, TriageValidator.validate(g, classifier.classify(g))));
        }
        return TriageReport.markdown(items, classifierName);
    }
}
