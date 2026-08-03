package com.marion.dmv.agent;

public record PendingReferral(
        String threadId,
        String question,
        String referralReason,
        String referralForm
) {}
