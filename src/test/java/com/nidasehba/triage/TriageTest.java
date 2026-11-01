package com.nidasehba.triage;

import com.nidasehba.triage.FailureGrouper.FailureGroup;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public class TriageTest {

    private static final Path REPORTS = Path.of("src/test/resources/sample-reports");

    private static List<FailureGroup> groups() throws Exception {
        return FailureGrouper.group(SurefireParser.parseDirectory(REPORTS));
    }

    private static FailureGroup groupContaining(String testName) throws Exception {
        return groups().stream()
                .filter(g -> g.failures().stream().anyMatch(f -> f.testName().equals(testName)))
                .findFirst().orElseThrow();
    }

    @Test
    public void parsesFailuresAndErrorsButNotPassingTests() throws Exception {
        List<TestFailure> failures = SurefireParser.parseDirectory(REPORTS);

        assertEquals(failures.size(), 7);
        TestFailure refund = failures.stream().filter(f -> f.testName().equals("refundsPartialAmount")).findFirst().orElseThrow();
        assertEquals(refund.className(), "com.example.payments.PaymentApiTest");
        assertEquals(refund.type(), "java.lang.AssertionError");
        assertTrue(refund.details().contains("NullPointerException in RefundService.calculateRemaining"));
    }

    @Test
    public void rejectsReportsWithExternalEntities() throws Exception {
        Path evil = Files.createTempFile("TEST-evil", ".xml");
        Files.writeString(evil, "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]>"
                + "<testsuite><testcase name=\"t\" classname=\"c\"><failure message=\"&e;\"/></testcase></testsuite>");
        assertThrows(Exception.class, () -> SurefireParser.parse(evil));
    }

    @Test
    public void groupsFailuresWithTheSameCause() throws Exception {
        List<FailureGroup> groups = groups();

        assertEquals(groups.size(), 5, groups.stream().map(FailureGroup::signature).toList().toString());
        assertEquals(groupContaining("refundsPartialAmount").failures().size(), 2, "both refund tests hit the same 500");
        assertEquals(groupContaining("checkoutWithWallet").failures().size(), 2, "both pay-now tests lost the same locator");
    }

    @Test
    public void signatureIgnoresVolatileIds() {
        TestFailure a = new TestFailure("C", "t1", "java.lang.AssertionError", "Customer cust_8a7f21 not found after 1200 ms", "");
        TestFailure b = new TestFailure("C", "t2", "java.lang.AssertionError", "Customer cust_99c3e0 not found after 980 ms", "");
        assertEquals(FailureGrouper.signature(a), FailureGrouper.signature(b));
    }

    @Test
    public void heuristicClassifierLabelsEachSampleGroup() throws Exception {
        HeuristicClassifier h = new HeuristicClassifier();
        Map<String, String> expected = Map.of(
                "refundsPartialAmount", "PRODUCT_BUG",
                "appliesCashbackOffer", "ENVIRONMENT",
                "checkoutWithSavedCard", "FLAKY",
                "checkoutWithNewCard", "TEST_BUG",
                "fetchesCustomerPayments", "TEST_DATA");
        for (Map.Entry<String, String> e : expected.entrySet()) {
            FailureGroup g = groupContaining(e.getKey());
            Triage t = h.classify(g);
            assertEquals(t.category(), e.getValue(), e.getKey());
            assertFalse(TriageValidator.validate(g, t).needsHuman(), e.getKey() + " evidence must be real");
        }
    }

    @Test
    public void validatorFlagsInventedEvidenceLowConfidenceAndUnknownCategory() throws Exception {
        FailureGroup g = groupContaining("refundsPartialAmount");

        TriageValidator.Result invented = TriageValidator.validate(g, new Triage("PRODUCT_BUG", 0.9,
                "Database deadlock", List.of("Deadlock found when trying to get lock"), "Raise a bug"));
        assertTrue(invented.needsHuman());
        assertTrue(invented.reasons().get(0).startsWith("evidence not found"));

        assertTrue(TriageValidator.validate(g, new Triage("PRODUCT_BUG", 0.4, "x",
                List.of("NullPointerException in RefundService.calculateRemaining"), "y")).needsHuman());
        assertTrue(TriageValidator.validate(g, new Triage("UNKNOWN", 0.9, "x",
                List.of("NullPointerException in RefundService.calculateRemaining"), "y")).needsHuman());
    }

    @Test
    public void reportShowsTotalsAndRoutesDoubtfulGroupsToAHuman() throws Exception {
        Classifier unsure = g -> new Triage("FLAKY", 0.3, "Not sure", List.of(g.sample().message()), "Re-run");

        String report = App.run(REPORTS, unsure, "test");

        assertTrue(report.contains("7 failed tests in 5 groups"));
        assertTrue(report.contains("| NEEDS_HUMAN | 7 |"));
        assertTrue(report.contains("low confidence (0.30)"));
    }

    @Test
    public void heuristicReportCountsTestsPerCategory() throws Exception {
        String report = App.run(REPORTS, new HeuristicClassifier(), "rule-based");
        Map<String, Long> lines = report.lines().filter(l -> l.startsWith("| ") && !l.startsWith("| Category"))
                .collect(Collectors.toMap(l -> l.split("\\|")[1].strip(), l -> Long.parseLong(l.split("\\|")[2].strip())));

        assertEquals(lines, Map.of("PRODUCT_BUG", 2L, "TEST_BUG", 2L, "FLAKY", 1L, "ENVIRONMENT", 1L, "TEST_DATA", 1L));
    }
}
