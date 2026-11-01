# AI Test Failure Triage

When a nightly regression run fails 40 tests, someone has to work out which failures are real bugs and
which are flaky tests, broken environments or bad test data. This tool reads the **JUnit/TestNG XML
reports**, groups failures that share a cause, classifies each group with the **Claude API**, checks
the AI's answer against the actual stack traces, and writes a **Markdown triage report**.

Built with **Java 17**, the **Anthropic Java SDK** (structured output), **TestNG** and **GitHub Actions** (a GitLab CI file is included too).

## How it works

1. **Parse**: every `failure` and `error` in `TEST-*.xml` (Maven Surefire / TestNG format). External XML entities are refused, since reports can come from anywhere.
2. **Group**: failures are grouped by a signature (exception type + message with ids and numbers replaced), so two refund tests hit by the same 500 become one item. HTTP status codes are kept, so a 500 and a 404 never merge.
3. **Classify** each group as `PRODUCT_BUG`, `TEST_BUG`, `FLAKY`, `ENVIRONMENT` or `TEST_DATA`, with a root cause, evidence and a next step. Claude is used when an API key is set; otherwise a rule-based classifier runs.
4. **Validate**: a classification is only trusted if the category is valid, confidence is at least 0.6, and **every evidence line it quotes really appears in the failure text**. Anything else is marked `NEEDS_HUMAN` instead of being reported as fact.

## Example (rule-based run on the sample reports)

```
7 failed tests in 5 groups. Classifier: rule-based.

| Category    | Failed tests |
|-------------|--------------|
| ENVIRONMENT | 1            |
| FLAKY       | 1            |
| PRODUCT_BUG | 2            |
| TEST_BUG    | 2            |
| TEST_DATA   | 1            |

## 1. PRODUCT_BUG (2 tests)
Signature: AssertionError: <n> expectation failed. Expected status code <201> but was <500>.
Evidence:  Response body: {"error":{"code":"INTERNAL_ERROR","message":"NullPointerException in RefundService..."}}
Next step: Raise a bug with the service owner, attaching the response body
```

## Run it

```bash
mvn -q package
java -jar target/ai-test-failure-triage.jar target/surefire-reports              # your own run
java -jar target/ai-test-failure-triage.jar src/test/resources/sample-reports    # the samples
```

With `ANTHROPIC_API_KEY` set it uses Claude; add `--heuristic` to force the rule-based classifier.
`CLAUDE_MODEL` overrides the model (default `claude-opus-5-5`).

## Tests

- Parser, grouping, the rule-based classifier and the report run on sample Surefire reports with no API key.
- `TriageValidator` tests prove that invented evidence, low confidence and unknown categories are routed to a human.
- `LiveClassifierTest` (only with an API key) requires Claude to agree with the expected label on at least 4 of 5 groups and never to quote evidence that isn't in the trace.

## Design choices

- **Group before you classify**: one call per root cause instead of per test keeps cost down and the report short.
- **Never trust the model's evidence blindly**: quoting lines that are not in the trace is the most common way an AI triage goes wrong, so it is checked mechanically.
- **Rule-based fallback**: the tool still works without a key, and the rules give a baseline to compare the AI against.
