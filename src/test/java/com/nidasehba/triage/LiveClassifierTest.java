package com.nidasehba.triage;

import com.nidasehba.triage.FailureGrouper.FailureGroup;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.util.List;

import static org.testng.Assert.assertTrue;

/**
 * Compares Claude's labels with the expected ones on the sample reports. Skipped unless
 * ANTHROPIC_API_KEY is set. Requires at least 4 of 5 groups to match and all evidence to be genuine.
 */
public class LiveClassifierTest {

    @Test(groups = "llm")
    public void claudeAgreesWithTheExpectedLabels() throws Exception {
        if (System.getenv("ANTHROPIC_API_KEY") == null) {
            throw new SkipException("ANTHROPIC_API_KEY not set");
        }
        List<FailureGroup> groups = FailureGrouper.group(SurefireParser.parseDirectory(Path.of("src/test/resources/sample-reports")));
        HeuristicClassifier expected = new HeuristicClassifier();
        ClaudeClassifier claude = ClaudeClassifier.fromEnv();

        int agree = 0;
        for (FailureGroup g : groups) {
            Triage t = claude.classify(g);
            TriageValidator.Result r = TriageValidator.validate(g, t);
            assertTrue(r.reasons().stream().noneMatch(s -> s.startsWith("evidence not found")), r.reasons().toString());
            if (t.category().equals(expected.classify(g).category())) {
                agree++;
            }
        }
        assertTrue(agree >= 4, "agreed on " + agree + " of " + groups.size());
    }
}
