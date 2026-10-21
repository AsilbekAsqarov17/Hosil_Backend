package uz.hosil.platform.identity.web;

import java.util.List;
import java.util.UUID;

public record UserSummaryDto(
        UUID id,
        String phoneNumber,
        String status,
        List<String> roles
) {}
