package com.nidasehba.triage;

import com.nidasehba.triage.FailureGrouper.FailureGroup;

public interface Classifier {

    Triage classify(FailureGroup group);
}
