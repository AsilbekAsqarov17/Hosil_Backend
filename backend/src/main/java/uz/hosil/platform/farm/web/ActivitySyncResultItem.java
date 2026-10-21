package uz.hosil.platform.farm.web;

import java.time.Instant;
import java.util.UUID;

public record ActivitySyncResultItem(
        String clientMutationId,
        UUID entityId,
        String status,
        Long serverSyncVersion,
        Instant serverUpdatedAt,
        String message
) {}
