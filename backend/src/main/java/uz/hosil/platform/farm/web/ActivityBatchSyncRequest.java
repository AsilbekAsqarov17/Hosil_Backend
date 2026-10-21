package uz.hosil.platform.farm.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ActivityBatchSyncRequest(
        @NotBlank(message = "clientBatchSyncId is required")
        String clientBatchSyncId,

        @NotNull(message = "mutations list is required")
        @Size(max = 50, message = "Batch size exceeds maximum limit of 50 mutations")
        @Valid
        List<ActivityMutationItem> mutations
) {}
