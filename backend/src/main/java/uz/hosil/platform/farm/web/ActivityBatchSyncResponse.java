package uz.hosil.platform.farm.web;

import java.time.Instant;
import java.util.List;

public record ActivityBatchSyncResponse(
        String clientBatchSyncId,
        Instant processedAt,
        List<ActivitySyncResultItem> results
) {}
