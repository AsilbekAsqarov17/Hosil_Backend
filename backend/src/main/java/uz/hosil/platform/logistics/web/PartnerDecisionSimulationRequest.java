package uz.hosil.platform.logistics.web;

import jakarta.validation.constraints.NotBlank;

public record PartnerDecisionSimulationRequest(
        @NotBlank(message = "Simulyatsiya qilinayotgan qaror koʻrsatilishi shart (APPROVED, REJECTED, ADDITIONAL_INFO_REQUESTED)")
        String decision
) {
}
