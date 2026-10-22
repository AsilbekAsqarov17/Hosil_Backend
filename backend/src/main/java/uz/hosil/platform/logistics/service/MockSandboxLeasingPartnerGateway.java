package uz.hosil.platform.logistics.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Deterministic mock sandbox implementation of LeasingPartnerPort.
 * Provides verifiable simulated underwriting decisions for automated integration testing and pilot demonstrations.
 * In strict compliance with PRD LEASE-004 and DEC-013, all decisions are marked is_simulated=true.
 */
@Component
public class MockSandboxLeasingPartnerGateway implements LeasingPartnerPort {

    private static final Logger log = LoggerFactory.getLogger(MockSandboxLeasingPartnerGateway.class);

    private static final String DEFAULT_PARTNER_CODE = "MOCK_AGROBANK_SANDBOX";
    private static final String DEFAULT_PARTNER_NAME = "Agrobank Mock Sandbox";
    private static final String SIMULATION_DISCLAIMER =
            "Ushbu lizing qarori va moliyalashtirish shartlari sinov simulyatsiyasi hisoblanadi (DEC-013). Haqiqiy bank kredit majburiyatini yuklamaydi.";

    private final BigDecimal annualInterestRate;
    private final String partnerCode;
    private final String partnerName;

    // Test simulation controls
    private volatile String simulatedDecisionOverride = null;
    private volatile boolean simulatedNetworkFailure = false;

    public MockSandboxLeasingPartnerGateway(
            @Value("${hosil.logistics.leasing.interest-rate-annual:0.18}") BigDecimal annualInterestRate,
            @Value("${hosil.logistics.leasing.sandbox.partner-code:MOCK_AGROBANK_SANDBOX}") String partnerCode,
            @Value("${hosil.logistics.leasing.sandbox.partner-name:Agrobank Mock Sandbox}") String partnerName
    ) {
        this.annualInterestRate = annualInterestRate != null ? annualInterestRate : new BigDecimal("0.18");
        this.partnerCode = partnerCode != null ? partnerCode : DEFAULT_PARTNER_CODE;
        this.partnerName = partnerName != null ? partnerName : DEFAULT_PARTNER_NAME;
    }

    @Override
    public PartnerDecisionResult submitForUnderwriting(PartnerSubmissionRequest request) {
        if (simulatedNetworkFailure) {
            log.error("MockSandboxLeasingPartnerGateway: Simulated network failure triggered for application: {}", request.applicationNumber());
            throw new IllegalStateException("Simulated partner bank network timeout during underwriting handoff");
        }

        String partnerRef = "SIM-AGRO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("Processing mock sandbox underwriting for applicationNumber: {}, equipment: {}, cost: {}",
                request.applicationNumber(), request.equipmentModel(), request.estimatedEquipmentCost());

        if (simulatedDecisionOverride != null) {
            log.warn("MockSandboxLeasingPartnerGateway: Applying override decision: {}", simulatedDecisionOverride);
            return buildOverriddenDecision(request, partnerRef, simulatedDecisionOverride);
        }

        // Rule 1: Minimum down payment requirement of 20%
        BigDecimal cost = request.estimatedEquipmentCost();
        BigDecimal downPayment = request.proposedDownPayment();
        BigDecimal minDownPayment = cost.multiply(new BigDecimal("0.20"));

        if (downPayment.compareTo(minDownPayment) < 0) {
            return new PartnerDecisionResult(
                    partnerCode,
                    partnerRef,
                    PartnerDecisionResult.DECISION_REJECTED,
                    null,
                    downPayment,
                    annualInterestRate,
                    request.desiredTermMonths(),
                    null,
                    "Boshlangʻich toʻlov miqdori minimal 20% dan kam (Talab: " + minDownPayment.toPlainString() + " " + request.currency() + ")",
                    "Kredit siyosati boʻyicha dastlabki badal yetarli emas",
                    true,
                    SIMULATION_DISCLAIMER
            );
        }

        // Rule 2: Calculate approved principal and linear monthly installment
        BigDecimal principal = cost.subtract(downPayment);
        int termMonths = request.desiredTermMonths() != null && request.desiredTermMonths() > 0 ? request.desiredTermMonths() : 24;

        // Interest = Principal * Rate * (TermMonths / 12)
        BigDecimal termYears = BigDecimal.valueOf(termMonths).divide(BigDecimal.valueOf(12), 4, RoundingMode.HALF_UP);
        BigDecimal totalInterest = principal.multiply(annualInterestRate).multiply(termYears).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalRepayment = principal.add(totalInterest);
        BigDecimal monthlyInstallment = totalRepayment.divide(BigDecimal.valueOf(termMonths), 2, RoundingMode.HALF_UP);

        return new PartnerDecisionResult(
                partnerCode,
                partnerRef,
                PartnerDecisionResult.DECISION_APPROVED,
                principal,
                downPayment,
                annualInterestRate,
                termMonths,
                monthlyInstallment,
                null,
                "Avtomatlashtirilgan skorinq natijasida lizing arizasi ijobiy maʼqullandi",
                true,
                SIMULATION_DISCLAIMER
        );
    }

    @Override
    public PartnerDecisionResult queryUnderwritingStatus(String partnerApplicationRef) {
        return new PartnerDecisionResult(
                partnerCode,
                partnerApplicationRef,
                PartnerDecisionResult.DECISION_APPROVED,
                new BigDecimal("200000000.00"),
                new BigDecimal("50000000.00"),
                annualInterestRate,
                24,
                new BigDecimal("9833333.33"),
                null,
                "Inquiry status: Active approved in sandbox",
                true,
                SIMULATION_DISCLAIMER
        );
    }

    private PartnerDecisionResult buildOverriddenDecision(PartnerSubmissionRequest request, String partnerRef, String decision) {
        if (PartnerDecisionResult.DECISION_REJECTED.equalsIgnoreCase(decision)) {
            return new PartnerDecisionResult(
                    partnerCode,
                    partnerRef,
                    PartnerDecisionResult.DECISION_REJECTED,
                    null,
                    request.proposedDownPayment(),
                    annualInterestRate,
                    request.desiredTermMonths(),
                    null,
                    "Moliyaviy koʻrsatkichlar bank kredit talablariga mos kelmadi (Simulyatsiya)",
                    "Qoʻshimcha garov taʼminoti talab etiladi",
                    true,
                    SIMULATION_DISCLAIMER
            );
        } else if (PartnerDecisionResult.DECISION_INFO_REQUESTED.equalsIgnoreCase(decision)) {
            return new PartnerDecisionResult(
                    partnerCode,
                    partnerRef,
                    PartnerDecisionResult.DECISION_INFO_REQUESTED,
                    null,
                    request.proposedDownPayment(),
                    annualInterestRate,
                    request.desiredTermMonths(),
                    null,
                    null,
                    "Oxirgi 6 oylik bank hisobvaragʻi aylanmasi talab etiladi",
                    true,
                    SIMULATION_DISCLAIMER
            );
        } else {
            BigDecimal cost = request.estimatedEquipmentCost();
            BigDecimal downPayment = request.proposedDownPayment();
            BigDecimal principal = cost.subtract(downPayment);
            int termMonths = request.desiredTermMonths() != null ? request.desiredTermMonths() : 24;
            BigDecimal termYears = BigDecimal.valueOf(termMonths).divide(BigDecimal.valueOf(12), 4, RoundingMode.HALF_UP);
            BigDecimal totalInterest = principal.multiply(annualInterestRate).multiply(termYears).setScale(2, RoundingMode.HALF_UP);
            BigDecimal monthlyInstallment = principal.add(totalInterest).divide(BigDecimal.valueOf(termMonths), 2, RoundingMode.HALF_UP);

            return new PartnerDecisionResult(
                    partnerCode,
                    partnerRef,
                    PartnerDecisionResult.DECISION_APPROVED,
                    principal,
                    downPayment,
                    annualInterestRate,
                    termMonths,
                    monthlyInstallment,
                    null,
                    "Override approved",
                    true,
                    SIMULATION_DISCLAIMER
            );
        }
    }

    @Override
    public String getPartnerCode() {
        return partnerCode;
    }

    @Override
    public String getPartnerName() {
        return partnerName;
    }

    // Testing mutators
    public void setSimulatedDecisionOverride(String simulatedDecisionOverride) {
        this.simulatedDecisionOverride = simulatedDecisionOverride;
    }

    public void setSimulatedNetworkFailure(boolean simulatedNetworkFailure) {
        this.simulatedNetworkFailure = simulatedNetworkFailure;
    }

    public void reset() {
        this.simulatedDecisionOverride = null;
        this.simulatedNetworkFailure = false;
    }
}
