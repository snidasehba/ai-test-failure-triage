package com.nidasehba.triage;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.nidasehba.triage.FailureGrouper.FailureGroup;

import java.util.stream.Collectors;

/** Classifies a failure group with Claude, returning a structured {@link Triage}. */
public class ClaudeClassifier implements Classifier {

    static final String SYSTEM = String.join("\n",
            "You triage automated test failures for a fintech QA team. Pick exactly one category:",
            "PRODUCT_BUG: the application behaved wrongly (5xx, wrong data, wrong status for valid input).",
            "TEST_BUG: the test itself is wrong (outdated locator, wrong expectation, bad setup code).",
            "FLAKY: timing or ordering problems that would likely pass on re-run (stale elements, races).",
            "ENVIRONMENT: infrastructure, network, dependency or configuration problems.",
            "TEST_DATA: the test relies on data that is missing or in the wrong state.",
            "Quote evidence exactly as it appears in the failure text; do not paraphrase it.",
            "If the evidence is thin, say so with a low confidence instead of guessing.");

    private static final int MAX_TRACE_LINES = 40;

    private final AnthropicClient client;
    private final String model;

    public ClaudeClassifier(AnthropicClient client, String model) {
        this.client = client;
        this.model = model;
    }

    public static ClaudeClassifier fromEnv() {
        return new ClaudeClassifier(AnthropicOkHttpClient.fromEnv(),
                System.getenv().getOrDefault("CLAUDE_MODEL", "claude-opus-5-5"));
    }

    @Override
    public Triage classify(FailureGroup group) {
        String tests = group.failures().stream().map(TestFailure::fullName).limit(10)
                .collect(Collectors.joining("\n"));
        String trace = group.sample().evidenceText().lines().limit(MAX_TRACE_LINES)
                .collect(Collectors.joining("\n"));
        StructuredMessageCreateParams<Triage> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(4000L)
                .system(SYSTEM)
                .outputConfig(Triage.class)
                .addUserMessage("Affected tests (" + group.failures().size() + "):\n" + tests
                        + "\n\nException type: " + group.sample().type()
                        + "\n\nFailure text:\n<failure>\n" + trace + "\n</failure>")
                .build();
        return client.messages().create(params).content().stream()
                .flatMap(block -> block.text().stream())
                .map(typed -> typed.text())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No triage in the model response"));
    }
}
