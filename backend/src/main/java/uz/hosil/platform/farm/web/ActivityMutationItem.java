package uz.hosil.platform.farm.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record ActivityMutationItem(
        @NotBlank(message = "clientMutationId is required")
        String clientMutationId,

        @NotBlank(message = "action is required")
        @Pattern(regexp = "^(CREATE|UPDATE)$", message = "action must be CREATE or UPDATE")
        String action,

        @NotNull(message = "entityId is required")
        UUID entityId,

        @NotNull(message = "clientSyncVersion is required")
        Long clientSyncVersion,

        @NotNull(message = "payload is required")
        @Valid
        CreateFarmActivityRequest payload
) {}
