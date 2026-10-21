package uz.hosil.platform.farm.web;

import java.time.LocalDate;

public record UpdateCropCycleRequest(
        LocalDate expectedHarvestDate,
        LocalDate actualHarvestDate,
        String status
) {}
