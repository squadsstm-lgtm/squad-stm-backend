package com.squad.backend.constants;

import java.util.Arrays;
import java.util.List;

public final class WithdrawalStatus {

    private WithdrawalStatus() {
    }

    public static final String WAITING_FOR_APPROVAL = "WAITING_FOR_APPROVAL";

    public static final String VERIFIED = "VERIFIED";

    public static final String PROCESSING = "PROCESSING";

    public static final String COMPLETED = "COMPLETED";

    public static final String FAILED = "FAILED";

    public static final List<String> ALL = Arrays.asList(
            WAITING_FOR_APPROVAL,
            VERIFIED,
            PROCESSING,
            COMPLETED,
            FAILED
    );
}
