package com.nidasehba.triage;

/** One failed or errored test case read from a JUnit/TestNG XML report. */
public record TestFailure(String className, String testName, String type, String message, String details) {

    public String fullName() {
        return className + "." + testName;
    }

    /** Message plus stack trace: the only text the classifier's evidence may quote from. */
    public String evidenceText() {
        return (message == null ? "" : message) + "\n" + (details == null ? "" : details);
    }
}
