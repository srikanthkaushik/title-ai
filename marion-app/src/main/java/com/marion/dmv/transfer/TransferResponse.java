package com.marion.dmv.transfer;

import java.util.List;
import java.util.Map;

public record TransferResponse(
        String reasoning,
        boolean supervisorReferral,
        String referralReason,
        String referralForm,
        List<String> checklist,
        List<String> conditionalChecklist,
        String conditionalNote,
        Map<String, Object> fees,
        Double taxOwed,
        List<String> sources
) {}
