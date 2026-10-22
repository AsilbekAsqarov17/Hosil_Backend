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
 * Agricultural equipment listing available for leasing or hire purchase.
 * Conforms to docs/04_domain_model.md §4.5 and PRD LEASE-001.
 */
@Entity
@Table(name = "equipment_listings")
public class EquipmentListingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "model_name", nullable = false, length = 150)
    private String modelName;

    @Column(name = "manufacturer", nullable = false, length = 150)
    private String manufacturer;

    @Column(name = "year_of_manufacture")
    private Integer yearOfManufacture;

    @Column(name = "condition", nullable = false, length = 30)
    private String condition;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "specifications", columnDefinition = "TEXT")
    private String specifications;

    @Column(name = "region", nullable = false, length = 100)
    private String region;

    @Column(name = "district", nullable = false, length = 100)
    private String district;

    @Column(name = "image_urls", columnDefinition = "TEXT")
    private String imageUrls;

    @Column(name = "daily_rate", precision = 14, scale = 2)
    private BigDecimal dailyRate;

    @Column(name = "monthly_rate", nullable = false, precision = 14, scale = 2)
    private BigDecimal monthlyRate;

    @Column(name = "market_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal marketValue;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "min_down_payment_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal minDownPaymentPercent;

    @Column(name = "min_term_months", nullable = false)
    private Integer minTermMonths;

    @Column(name = "max_term_months", nullable = false)
    private Integer maxTermMonths;

    @Column(name = "availability_status", nullable = false, length = 30)
    private String availabilityStatus;

    @Column(name = "publication_status", nullable = false, length = 30)
    private String publicationStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public EquipmentListingEntity() {
        this.condition = "GOOD";
        this.currency = "UZS";
        this.minDownPaymentPercent = new BigDecimal("20.00");
        this.minTermMonths = 12;
        this.maxTermMonths = 36;
        this.availabilityStatus = "AVAILABLE";
        this.publicationStatus = "PUBLISHED";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.version = 0L;
    }

    public EquipmentListingEntity(
            UUID ownerUserId,
            String category,
            String title,
            String modelName,
            String manufacturer,
            Integer yearOfManufacture,
            String condition,
            String description,
            String specifications,
            String region,
            String district,
            String imageUrls,
            BigDecimal dailyRate,
            BigDecimal monthlyRate,
            BigDecimal marketValue,
            String currency,
            BigDecimal minDownPaymentPercent,
            Integer minTermMonths,
            Integer maxTermMonths
    ) {
        this();
        this.ownerUserId = ownerUserId;
        this.category = category;
        this.title = title;
        this.modelName = modelName;
        this.manufacturer = manufacturer;
        this.yearOfManufacture = yearOfManufacture;
        if (condition != null) this.condition = condition;
        this.description = description;
        this.specifications = specifications;
        this.region = region;
        this.district = district;
        this.imageUrls = imageUrls;
        this.dailyRate = dailyRate;
        this.monthlyRate = monthlyRate;
        this.marketValue = marketValue;
        if (currency != null) this.currency = currency;
        if (minDownPaymentPercent != null) this.minDownPaymentPercent = minDownPaymentPercent;
        if (minTermMonths != null) this.minTermMonths = minTermMonths;
        if (maxTermMonths != null) this.maxTermMonths = maxTermMonths;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(UUID ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public Integer getYearOfManufacture() {
        return yearOfManufacture;
    }

    public void setYearOfManufacture(Integer yearOfManufacture) {
        this.yearOfManufacture = yearOfManufacture;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSpecifications() {
        return specifications;
    }

    public void setSpecifications(String specifications) {
        this.specifications = specifications;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(String imageUrls) {
        this.imageUrls = imageUrls;
    }

    public BigDecimal getDailyRate() {
        return dailyRate;
    }

    public void setDailyRate(BigDecimal dailyRate) {
        this.dailyRate = dailyRate;
    }

    public BigDecimal getMonthlyRate() {
        return monthlyRate;
    }

    public void setMonthlyRate(BigDecimal monthlyRate) {
        this.monthlyRate = monthlyRate;
    }

    public BigDecimal getMarketValue() {
        return marketValue;
    }

    public void setMarketValue(BigDecimal marketValue) {
        this.marketValue = marketValue;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getMinDownPaymentPercent() {
        return minDownPaymentPercent;
    }

    public void setMinDownPaymentPercent(BigDecimal minDownPaymentPercent) {
        this.minDownPaymentPercent = minDownPaymentPercent;
    }

    public Integer getMinTermMonths() {
        return minTermMonths;
    }

    public void setMinTermMonths(Integer minTermMonths) {
        this.minTermMonths = minTermMonths;
    }

    public Integer getMaxTermMonths() {
        return maxTermMonths;
    }

    public void setMaxTermMonths(Integer maxTermMonths) {
        this.maxTermMonths = maxTermMonths;
    }

    public String getAvailabilityStatus() {
        return availabilityStatus;
    }

    public void setAvailabilityStatus(String availabilityStatus) {
        this.availabilityStatus = availabilityStatus;
        this.updatedAt = Instant.now();
    }

    public String getPublicationStatus() {
        return publicationStatus;
    }

    public void setPublicationStatus(String publicationStatus) {
        this.publicationStatus = publicationStatus;
        this.updatedAt = Instant.now();
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
