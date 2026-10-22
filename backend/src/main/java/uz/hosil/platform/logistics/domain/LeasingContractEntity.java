package uz.hosil.platform.logistics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Leasing financing agreement entity.
 * Represents executed or pending contractual terms derived from partner financing decisions.
 */
@Entity
@Table(name = "leasing_contracts")
public class LeasingContractEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "contract_number", nullable = false, unique = true, length = 50)
    private String contractNumber;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "lessee_user_id", nullable = false)
    private UUID lesseeUserId;

    @Column(name = "lessor_user_id", nullable = false)
    private UUID lessorUserId;

    @Column(name = "partner_bank_code", nullable = false, length = 50)
    private String partnerBankCode;

    @Column(name = "approved_principal", nullable = false, precision = 14, scale = 2)
    private BigDecimal approvedPrincipal;

    @Column(name = "approved_down_payment", nullable = false, precision = 14, scale = 2)
    private BigDecimal approvedDownPayment;

    @Column(name = "annual_interest_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal annualInterestRate;

    @Column(name = "term_months", nullable = false)
    private Integer termMonths;

    @Column(name = "monthly_installment", nullable = false, precision = 14, scale = 2)
    private BigDecimal monthlyInstallment;

    @Column(name = "total_repayment", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalRepayment;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Column(name = "is_simulated", nullable = false)
    private Boolean isSimulated;

    @Column(name = "disclaimer", columnDefinition = "TEXT")
    private String disclaimer;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public LeasingContractEntity() {
        this.status = "PENDING_SIGNATURE";
        this.currency = "UZS";
        this.isSimulated = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.version = 0L;
    }

    public LeasingContractEntity(
            String contractNumber,
            UUID applicationId,
            UUID equipmentId,
            UUID lesseeUserId,
            UUID lessorUserId,
            String partnerBankCode,
            BigDecimal approvedPrincipal,
            BigDecimal approvedDownPayment,
            BigDecimal annualInterestRate,
            Integer termMonths,
            BigDecimal monthlyInstallment,
            BigDecimal totalRepayment,
            String currency,
            LocalDate startDate,
            LocalDate endDate
    ) {
        this();
        this.contractNumber = contractNumber;
        this.applicationId = applicationId;
        this.equipmentId = equipmentId;
        this.lesseeUserId = lesseeUserId;
        this.lessorUserId = lessorUserId;
        this.partnerBankCode = partnerBankCode;
        this.approvedPrincipal = approvedPrincipal;
        this.approvedDownPayment = approvedDownPayment;
        this.annualInterestRate = annualInterestRate;
        this.termMonths = termMonths;
        this.monthlyInstallment = monthlyInstallment;
        this.totalRepayment = totalRepayment;
        if (currency != null) this.currency = currency;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public UUID getId() {
        return id;
    }

    public String getContractNumber() {
        return contractNumber;
    }

    public void setContractNumber(String contractNumber) {
        this.contractNumber = contractNumber;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(UUID applicationId) {
        this.applicationId = applicationId;
    }

    public UUID getEquipmentId() {
        return equipmentId;
    }

    public void setEquipmentId(UUID equipmentId) {
        this.equipmentId = equipmentId;
    }

    public UUID getLesseeUserId() {
        return lesseeUserId;
    }

    public void setLesseeUserId(UUID lesseeUserId) {
        this.lesseeUserId = lesseeUserId;
    }

    public UUID getLessorUserId() {
        return lessorUserId;
    }

    public void setLessorUserId(UUID lessorUserId) {
        this.lessorUserId = lessorUserId;
    }

    public String getPartnerBankCode() {
        return partnerBankCode;
    }

    public void setPartnerBankCode(String partnerBankCode) {
        this.partnerBankCode = partnerBankCode;
    }

    public BigDecimal getApprovedPrincipal() {
        return approvedPrincipal;
    }

    public void setApprovedPrincipal(BigDecimal approvedPrincipal) {
        this.approvedPrincipal = approvedPrincipal;
    }

    public BigDecimal getApprovedDownPayment() {
        return approvedDownPayment;
    }

    public void setApprovedDownPayment(BigDecimal approvedDownPayment) {
        this.approvedDownPayment = approvedDownPayment;
    }

    public BigDecimal getAnnualInterestRate() {
        return annualInterestRate;
    }

    public void setAnnualInterestRate(BigDecimal annualInterestRate) {
        this.annualInterestRate = annualInterestRate;
    }

    public Integer getTermMonths() {
        return termMonths;
    }

    public void setTermMonths(Integer termMonths) {
        this.termMonths = termMonths;
    }

    public BigDecimal getMonthlyInstallment() {
        return monthlyInstallment;
    }

    public void setMonthlyInstallment(BigDecimal monthlyInstallment) {
        this.monthlyInstallment = monthlyInstallment;
    }

    public BigDecimal getTotalRepayment() {
        return totalRepayment;
    }

    public void setTotalRepayment(BigDecimal totalRepayment) {
        this.totalRepayment = totalRepayment;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public Instant getSignedAt() {
        return signedAt;
    }

    public void setSignedAt(Instant signedAt) {
        this.signedAt = signedAt;
    }

    public Boolean getIsSimulated() {
        return isSimulated;
    }

    public void setIsSimulated(Boolean isSimulated) {
        this.isSimulated = isSimulated;
    }

    public String getDisclaimer() {
        return disclaimer;
    }

    public void setDisclaimer(String disclaimer) {
        this.disclaimer = disclaimer;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
