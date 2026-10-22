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
import java.util.UUID;

/**
 * Leasing application entity representing a farmer's request for machinery financing.
 * Strictly adheres to docs/04_domain_model.md §4.5 (Table 31), PRD LEASE-001 - LEASE-005, and DEC-013.
 */
@Entity
@Table(name = "leasing_applications")
public class LeasingApplicationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "application_number", nullable = false, unique = true, length = 50)
    private String applicationNumber;

    @Column(name = "applicant_user_id", nullable = false)
    private UUID applicantUserId;

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "farm_id")
    private UUID farmId;

    @Column(name = "applicant_business_type", nullable = false, length = 50)
    private String applicantBusinessType;

    @Column(name = "applicant_name", nullable = false, length = 150)
    private String applicantName;

    @Column(name = "applicant_phone", nullable = false, length = 50)
    private String applicantPhone;

    @Column(name = "desired_term_months", nullable = false)
    private Integer desiredTermMonths;

    @Column(name = "proposed_down_payment", nullable = false, precision = 14, scale = 2)
    private BigDecimal proposedDownPayment;

    @Column(name = "estimated_equipment_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal estimatedEquipmentCost;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "intended_use_notes", columnDefinition = "TEXT")
    private String intendedUseNotes;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "partner_bank_code", length = 50)
    private String partnerBankCode;

    @Column(name = "partner_application_ref", length = 100)
    private String partnerApplicationRef;

    @Column(name = "approved_principal", precision = 14, scale = 2)
    private BigDecimal approvedPrincipal;

    @Column(name = "approved_term_months")
    private Integer approvedTermMonths;

    @Column(name = "approved_down_payment", precision = 14, scale = 2)
    private BigDecimal approvedDownPayment;

    @Column(name = "approved_monthly_installment", precision = 14, scale = 2)
    private BigDecimal approvedMonthlyInstallment;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;

    @Column(name = "is_simulated", nullable = false)
    private Boolean isSimulated;

    @Column(name = "disclaimer", columnDefinition = "TEXT")
    private String disclaimer;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public LeasingApplicationEntity() {
        this.status = "DRAFT";
        this.applicantBusinessType = "FERMER";
        this.currency = "UZS";
        this.isSimulated = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.version = 0L;
    }

    public LeasingApplicationEntity(
            String applicationNumber,
            UUID applicantUserId,
            UUID equipmentId,
            UUID farmId,
            String applicantBusinessType,
            String applicantName,
            String applicantPhone,
            Integer desiredTermMonths,
            BigDecimal proposedDownPayment,
            BigDecimal estimatedEquipmentCost,
            String currency,
            String intendedUseNotes,
            String idempotencyKey
    ) {
        this();
        this.applicationNumber = applicationNumber;
        this.applicantUserId = applicantUserId;
        this.equipmentId = equipmentId;
        this.farmId = farmId;
        if (applicantBusinessType != null) this.applicantBusinessType = applicantBusinessType;
        this.applicantName = applicantName;
        this.applicantPhone = applicantPhone;
        this.desiredTermMonths = desiredTermMonths;
        this.proposedDownPayment = proposedDownPayment;
        this.estimatedEquipmentCost = estimatedEquipmentCost;
        if (currency != null) this.currency = currency;
        this.intendedUseNotes = intendedUseNotes;
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getId() {
        return id;
    }

    public String getApplicationNumber() {
        return applicationNumber;
    }

    public void setApplicationNumber(String applicationNumber) {
        this.applicationNumber = applicationNumber;
    }

    public UUID getApplicantUserId() {
        return applicantUserId;
    }

    public void setApplicantUserId(UUID applicantUserId) {
        this.applicantUserId = applicantUserId;
    }

    public UUID getEquipmentId() {
        return equipmentId;
    }

    public void setEquipmentId(UUID equipmentId) {
        this.equipmentId = equipmentId;
    }

    public UUID getFarmId() {
        return farmId;
    }

    public void setFarmId(UUID farmId) {
        this.farmId = farmId;
    }

    public String getApplicantBusinessType() {
        return applicantBusinessType;
    }

    public void setApplicantBusinessType(String applicantBusinessType) {
        this.applicantBusinessType = applicantBusinessType;
    }

    public String getApplicantName() {
        return applicantName;
    }

    public void setApplicantName(String applicantName) {
        this.applicantName = applicantName;
    }

    public String getApplicantPhone() {
        return applicantPhone;
    }

    public void setApplicantPhone(String applicantPhone) {
        this.applicantPhone = applicantPhone;
    }

    public Integer getDesiredTermMonths() {
        return desiredTermMonths;
    }

    public void setDesiredTermMonths(Integer desiredTermMonths) {
        this.desiredTermMonths = desiredTermMonths;
    }

    public BigDecimal getProposedDownPayment() {
        return proposedDownPayment;
    }

    public void setProposedDownPayment(BigDecimal proposedDownPayment) {
        this.proposedDownPayment = proposedDownPayment;
    }

    public BigDecimal getEstimatedEquipmentCost() {
        return estimatedEquipmentCost;
    }

    public void setEstimatedEquipmentCost(BigDecimal estimatedEquipmentCost) {
        this.estimatedEquipmentCost = estimatedEquipmentCost;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getIntendedUseNotes() {
        return intendedUseNotes;
    }

    public void setIntendedUseNotes(String intendedUseNotes) {
        this.intendedUseNotes = intendedUseNotes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public String getPartnerBankCode() {
        return partnerBankCode;
    }

    public void setPartnerBankCode(String partnerBankCode) {
        this.partnerBankCode = partnerBankCode;
    }

    public String getPartnerApplicationRef() {
        return partnerApplicationRef;
    }

    public void setPartnerApplicationRef(String partnerApplicationRef) {
        this.partnerApplicationRef = partnerApplicationRef;
    }

    public BigDecimal getApprovedPrincipal() {
        return approvedPrincipal;
    }

    public void setApprovedPrincipal(BigDecimal approvedPrincipal) {
        this.approvedPrincipal = approvedPrincipal;
    }

    public Integer getApprovedTermMonths() {
        return approvedTermMonths;
    }

    public void setApprovedTermMonths(Integer approvedTermMonths) {
        this.approvedTermMonths = approvedTermMonths;
    }

    public BigDecimal getApprovedDownPayment() {
        return approvedDownPayment;
    }

    public void setApprovedDownPayment(BigDecimal approvedDownPayment) {
        this.approvedDownPayment = approvedDownPayment;
    }

    public BigDecimal getApprovedMonthlyInstallment() {
        return approvedMonthlyInstallment;
    }

    public void setApprovedMonthlyInstallment(BigDecimal approvedMonthlyInstallment) {
        this.approvedMonthlyInstallment = approvedMonthlyInstallment;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public String getReviewNotes() {
        return reviewNotes;
    }

    public void setReviewNotes(String reviewNotes) {
        this.reviewNotes = reviewNotes;
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

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
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
