package uz.hosil.platform.logistics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Monthly installment breakdown for an authorized leasing contract.
 */
@Entity
@Table(name = "leasing_repayment_schedules")
public class LeasingRepaymentScheduleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "contract_id", nullable = false)
    private UUID contractId;

    @Column(name = "installment_number", nullable = false)
    private Integer installmentNumber;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "principal_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal principalAmount;

    @Column(name = "interest_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal interestAmount;

    @Column(name = "total_installment", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalInstallment;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public LeasingRepaymentScheduleEntity() {
        this.status = "PENDING";
        this.currency = "UZS";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public LeasingRepaymentScheduleEntity(
            UUID contractId,
            Integer installmentNumber,
            LocalDate dueDate,
            BigDecimal principalAmount,
            BigDecimal interestAmount,
            BigDecimal totalInstallment,
            String currency
    ) {
        this();
        this.contractId = contractId;
        this.installmentNumber = installmentNumber;
        this.dueDate = dueDate;
        this.principalAmount = principalAmount;
        this.interestAmount = interestAmount;
        this.totalInstallment = totalInstallment;
        if (currency != null) this.currency = currency;
    }

    public UUID getId() {
        return id;
    }

    public UUID getContractId() {
        return contractId;
    }

    public Integer getInstallmentNumber() {
        return installmentNumber;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public BigDecimal getPrincipalAmount() {
        return principalAmount;
    }

    public BigDecimal getInterestAmount() {
        return interestAmount;
    }

    public BigDecimal getTotalInstallment() {
        return totalInstallment;
    }

    public String getCurrency() {
        return currency;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Instant paidAt) {
        this.paidAt = paidAt;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public void setPaymentReference(String paymentReference) {
        this.paymentReference = paymentReference;
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
}
