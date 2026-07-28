package com.marion.dmv.transfer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Deterministic post-processor applied to every parsed TransferResponse.
 * Fixes arithmetic and consistency issues that LLMs produce regardless of prompt quality.
 */
public final class TransferResponseValidator {

    private static final String[] FEE_COMPONENTS =
            {"titleFee", "vinFee", "registrationFee", "emissionsFee", "lienReleaseFee"};

    private static final String DEFAULT_CONDITIONAL_NOTE =
            "CONDITIONAL — SUBJECT TO SUPERVISOR REVIEW. DO NOT ACT ON THIS LIST UNTIL " +
            "THE SUPERVISOR HAS CLEARED YOUR TRANSACTION.";

    private TransferResponseValidator() {}

    public static TransferResponse validate(TransferResponse r) {
        return new TransferResponse(
                r.reasoning(),
                r.supervisorReferral(),
                r.referralReason(),
                // referralForm must be "TR-10" whenever supervisorReferral is true
                r.supervisorReferral() && r.referralForm() == null ? "TR-10" : r.referralForm(),
                // checklist must be null on a referral — model occasionally forgets
                r.supervisorReferral() ? null : r.checklist(),
                r.conditionalChecklist(),
                // conditionalNote must be present whenever there is a conditionalChecklist
                r.conditionalChecklist() != null && r.conditionalNote() == null
                        ? DEFAULT_CONDITIONAL_NOTE : r.conditionalNote(),
                // Recompute totalToDMV from components; LLMs routinely miscalculate
                fixFeeTotal(r.fees()),
                // taxOwed must be null on a referral
                r.supervisorReferral() ? null : r.taxOwed(),
                r.sources()
        );
    }

    /**
     * Recomputes totalToDMV as the sum of the five known fee components.
     * Returns the original map unchanged if the total is already correct (within $0.01)
     * or if fees is null.
     */
    private static Map<String, Object> fixFeeTotal(Map<String, Object> fees) {
        if (fees == null) return null;

        double computed = 0;
        for (String key : FEE_COMPONENTS) {
            Object v = fees.get(key);
            if (v instanceof Number n) computed += n.doubleValue();
        }
        computed = Math.round(computed * 100.0) / 100.0;

        Object stated = fees.get("totalToDMV");
        double statedTotal = stated instanceof Number n ? n.doubleValue() : Double.NaN;

        if (Double.isNaN(statedTotal) || Math.abs(computed - statedTotal) > 0.01) {
            Map<String, Object> fixed = new LinkedHashMap<>(fees);
            fixed.put("totalToDMV", computed);
            return fixed;
        }
        return fees;
    }
}
