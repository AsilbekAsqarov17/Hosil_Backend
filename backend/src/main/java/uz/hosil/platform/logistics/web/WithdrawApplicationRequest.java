package uz.hosil.platform.logistics.web;

import jakarta.validation.constraints.Size;

public record WithdrawApplicationRequest(
        @Size(max = 500)
        String reason
) {
}
