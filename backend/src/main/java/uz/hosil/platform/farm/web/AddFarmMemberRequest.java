package uz.hosil.platform.farm.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AddFarmMemberRequest(
        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^(\\+998)[0-9]{9}$", message = "Phone number must follow +998XXXXXXXXX format")
        String phoneNumber,

        @NotBlank(message = "Role is required")
        @Pattern(regexp = "^(OWNER|MANAGER|AGRONOMIST|WORKER)$", message = "Role must be one of OWNER, MANAGER, AGRONOMIST, WORKER")
        String role,

        String permissionsMask
) {}
