package uz.hosil.platform.logistics.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hosil.platform.common.error.BusinessConflictException;
import uz.hosil.platform.common.error.ResourceNotFoundException;
import uz.hosil.platform.logistics.domain.EquipmentListingEntity;
import uz.hosil.platform.logistics.domain.LeasingApplicationEntity;
import uz.hosil.platform.logistics.domain.LeasingApplicationStatusHistoryEntity;
import uz.hosil.platform.logistics.domain.LeasingContractEntity;
import uz.hosil.platform.logistics.domain.LeasingRepaymentScheduleEntity;
import uz.hosil.platform.logistics.repository.EquipmentListingRepository;
import uz.hosil.platform.logistics.repository.LeasingApplicationRepository;
import uz.hosil.platform.logistics.repository.LeasingApplicationStatusHistoryRepository;
import uz.hosil.platform.logistics.repository.LeasingContractRepository;
import uz.hosil.platform.logistics.repository.LeasingRepaymentScheduleRepository;
import uz.hosil.platform.logistics.web.CreateEquipmentListingRequest;
import uz.hosil.platform.logistics.web.EquipmentListingDto;
import uz.hosil.platform.logistics.web.LeasingApplicationDto;
import uz.hosil.platform.logistics.web.LeasingContractDto;
import uz.hosil.platform.logistics.web.PageEnvelope;
import uz.hosil.platform.logistics.web.PartnerDecisionSimulationRequest;
import uz.hosil.platform.logistics.web.RepaymentScheduleItemDto;
import uz.hosil.platform.logistics.web.SubmitLeasingApplicationRequest;
import uz.hosil.platform.logistics.web.UpdateEquipmentListingRequest;
import uz.hosil.platform.logistics.web.WithdrawApplicationRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Core business orchestrator for agricultural machinery listings,
 * leasing applications, partner financing integration, and contract execution.
 */
@Service
public class EquipmentLeasingService {

    private static final Logger log = LoggerFactory.getLogger(EquipmentLeasingService.class);

    private static final Set<String> ACTIVE_APPLICATION_STATUSES = Set.of(
            "SUBMITTED", "UNDER_REVIEW", "ADDITIONAL_INFO_REQUESTED", "APPROVED", "CONTRACT_PENDING"
    );

    private static final Set<String> ACTIVE_CONTRACT_STATUSES = Set.of(
            "PENDING_SIGNATURE", "ACTIVE"
    );

    private final EquipmentListingRepository equipmentRepository;
    private final LeasingApplicationRepository applicationRepository;
    private final LeasingApplicationStatusHistoryRepository historyRepository;
    private final LeasingContractRepository contractRepository;
    private final LeasingRepaymentScheduleRepository repaymentRepository;
    private final LeasingPartnerPort partnerPort;

    public EquipmentLeasingService(
            EquipmentListingRepository equipmentRepository,
            LeasingApplicationRepository applicationRepository,
            LeasingApplicationStatusHistoryRepository historyRepository,
            LeasingContractRepository contractRepository,
            LeasingRepaymentScheduleRepository repaymentRepository,
            LeasingPartnerPort partnerPort
    ) {
        this.equipmentRepository = equipmentRepository;
        this.applicationRepository = applicationRepository;
        this.historyRepository = historyRepository;
        this.contractRepository = contractRepository;
        this.repaymentRepository = repaymentRepository;
        this.partnerPort = partnerPort;
    }

    // =========================================================================
    // 1. Equipment Listings Lifecycle
    // =========================================================================

    @Transactional
    public EquipmentListingDto createEquipmentListing(CreateEquipmentListingRequest request, UUID ownerUserId) {
        EquipmentListingEntity entity = new EquipmentListingEntity(
                ownerUserId,
                request.category().toUpperCase().trim(),
                request.title().trim(),
                request.modelName().trim(),
                request.manufacturer().trim(),
                request.yearOfManufacture(),
                request.condition() != null ? request.condition().toUpperCase().trim() : "GOOD",
                request.description(),
                request.specifications(),
                request.region().trim(),
                request.district().trim(),
                request.imageUrls(),
                request.dailyRate(),
                request.monthlyRate(),
                request.marketValue(),
                request.currency() != null ? request.currency().toUpperCase().trim() : "UZS",
                request.minDownPaymentPercent() != null ? request.minDownPaymentPercent() : new BigDecimal("20.00"),
                request.minTermMonths() != null ? request.minTermMonths() : 12,
                request.maxTermMonths() != null ? request.maxTermMonths() : 36
        );

        EquipmentListingEntity saved = equipmentRepository.save(entity);
        log.info("Created new equipment listing id: {}, title: {}, owner: {}", saved.getId(), saved.getTitle(), ownerUserId);
        return EquipmentListingDto.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public EquipmentListingDto getEquipmentListing(UUID listingId) {
        EquipmentListingEntity entity = equipmentRepository.findById(listingId)
                .orElseThrow(() -> new ResourceNotFoundException("Texnika topilmadi: " + listingId));
        return EquipmentListingDto.fromEntity(entity);
    }

    @Transactional
    public EquipmentListingDto updateEquipmentListing(
            UUID listingId,
            UpdateEquipmentListingRequest request,
            UUID callerUserId,
            boolean isAdmin
    ) {
        EquipmentListingEntity entity = equipmentRepository.findByIdForUpdate(listingId)
                .orElseThrow(() -> new ResourceNotFoundException("Texnika topilmadi: " + listingId));

        if (!entity.getOwnerUserId().equals(callerUserId) && !isAdmin) {
            throw new AccessDeniedException("Faqat texnika egasi yoki administrator eʼlonni oʻzgartira oladi");
        }

        if (request.title() != null) entity.setTitle(request.title().trim());
        if (request.description() != null) entity.setDescription(request.description());
        if (request.specifications() != null) entity.setSpecifications(request.specifications());
        if (request.monthlyRate() != null) entity.setMonthlyRate(request.monthlyRate());
        if (request.dailyRate() != null) entity.setDailyRate(request.dailyRate());
        if (request.marketValue() != null) entity.setMarketValue(request.marketValue());
        if (request.minDownPaymentPercent() != null) entity.setMinDownPaymentPercent(request.minDownPaymentPercent());
        if (request.availabilityStatus() != null) entity.setAvailabilityStatus(request.availabilityStatus().toUpperCase());
        if (request.publicationStatus() != null) entity.setPublicationStatus(request.publicationStatus().toUpperCase());

        EquipmentListingEntity saved = equipmentRepository.save(entity);
        return EquipmentListingDto.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public PageEnvelope<EquipmentListingDto> listEquipmentListings(
            String category,
            String region,
            String availabilityStatus,
            Pageable pageable
    ) {
        Specification<EquipmentListingEntity> spec = (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("publicationStatus"), "PUBLISHED"));

            if (category != null && !category.isBlank() && !category.equalsIgnoreCase("ALL")) {
                predicates.add(cb.equal(root.get("category"), category.toUpperCase().trim()));
            }
            if (region != null && !region.isBlank() && !region.equalsIgnoreCase("Barchasi")) {
                predicates.add(cb.equal(root.get("region"), region.trim()));
            }
            if (availabilityStatus != null && !availabilityStatus.isBlank()) {
                predicates.add(cb.equal(root.get("availabilityStatus"), availabilityStatus.toUpperCase().trim()));
            } else {
                predicates.add(cb.equal(root.get("availabilityStatus"), "AVAILABLE"));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Page<EquipmentListingEntity> page = equipmentRepository.findAll(spec, pageable);
        return PageEnvelope.from(page.map(EquipmentListingDto::fromEntity));
    }

    // =========================================================================
    // 2. Leasing Applications Workflow
    // =========================================================================

    @Transactional
    public LeasingApplicationDto submitApplication(
            SubmitLeasingApplicationRequest request,
            UUID applicantUserId,
            boolean saveAsDraft
    ) {
        // Idempotency check
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            var existing = applicationRepository.findByApplicantUserIdAndIdempotencyKey(applicantUserId, request.idempotencyKey().trim());
            if (existing.isPresent()) {
                log.info("Idempotent hit for leasing application: {}", existing.get().getApplicationNumber());
                return LeasingApplicationDto.fromEntity(existing.get());
            }
        }

        // Lock equipment listing with pessimistic write lock
        EquipmentListingEntity equipment = equipmentRepository.findByIdForUpdate(request.equipmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Texnika topilmadi: " + request.equipmentId()));

        if (!"AVAILABLE".equalsIgnoreCase(equipment.getAvailabilityStatus())) {
            throw new BusinessConflictException("Ushbu texnika ayni damda lizing uchun mavjud emas (Holat: " + equipment.getAvailabilityStatus() + ")");
        }

        // Guard against duplicate active applications by the same applicant for the same equipment
        if (applicationRepository.existsByApplicantUserIdAndEquipmentIdAndStatusIn(applicantUserId, request.equipmentId(), ACTIVE_APPLICATION_STATUSES)) {
            throw new BusinessConflictException("Ushbu texnika uchun sizning faol arizangiz mavjud");
        }

        // Validate equipment cost and down payment
        BigDecimal cost = equipment.getMarketValue();
        BigDecimal downPayment = request.proposedDownPayment() != null ? request.proposedDownPayment() : BigDecimal.ZERO;

        String appNumber = generateApplicationNumber();
        LeasingApplicationEntity app = new LeasingApplicationEntity(
                appNumber,
                applicantUserId,
                equipment.getId(),
                request.farmId(),
                request.applicantBusinessType() != null ? request.applicantBusinessType().toUpperCase() : "FERMER",
                request.applicantName().trim(),
                request.applicantPhone().trim(),
                request.desiredTermMonths() != null ? request.desiredTermMonths() : equipment.getMinTermMonths(),
                downPayment,
                cost,
                equipment.getCurrency(),
                request.intendedUseNotes(),
                request.idempotencyKey()
        );

        if (saveAsDraft) {
            app.setStatus("DRAFT");
            LeasingApplicationEntity saved = applicationRepository.save(app);
            recordStatusHistory(saved.getId(), null, "DRAFT", applicantUserId, "APPLICANT", "Ariza qoralama sifatida saqlandi");
            return LeasingApplicationDto.fromEntity(saved);
        }

        // Active Submission Workflow
        app.setStatus("SUBMITTED");
        app.setSubmittedAt(Instant.now());
        LeasingApplicationEntity saved = applicationRepository.save(app);
        recordStatusHistory(saved.getId(), null, "SUBMITTED", applicantUserId, "APPLICANT", "Ariza rasmiylashtirildi va hamkor bankka yuborildi");

        // Forward to Partner Financial Underwriting SPI
        forwardToPartnerUnderwriting(saved, equipment);

        return LeasingApplicationDto.fromEntity(saved);
    }

    private void forwardToPartnerUnderwriting(LeasingApplicationEntity app, EquipmentListingEntity equipment) {
        try {
            PartnerSubmissionRequest partnerReq = new PartnerSubmissionRequest(
                    app.getId(),
                    app.getApplicationNumber(),
                    equipment.getId(),
                    equipment.getModelName(),
                    app.getEstimatedEquipmentCost(),
                    app.getProposedDownPayment(),
                    app.getDesiredTermMonths(),
                    app.getApplicantName(),
                    app.getApplicantPhone(),
                    app.getApplicantBusinessType(),
                    app.getCurrency()
            );

            PartnerDecisionResult decision = partnerPort.submitForUnderwriting(partnerReq);
            applyPartnerDecision(app, decision, equipment);
        } catch (Exception ex) {
            log.error("Failed to forward application to partner underwriting: {}", ex.getMessage());
            app.setStatus("UNDER_REVIEW");
            app.setReviewNotes("Hamkor bank bilan aloqa uzildi, koʻrib chiqish navbatga qoʻyildi");
            applicationRepository.save(app);
        }
    }

    @Transactional
    public LeasingApplicationDto processPartnerDecision(
            UUID applicationId,
            PartnerDecisionSimulationRequest overrideRequest,
            UUID callerUserId,
            boolean isAdmin
    ) {
        LeasingApplicationEntity app = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Ariza topilmadi: " + applicationId));

        if (!isAdmin && !app.getApplicantUserId().equals(callerUserId)) {
            throw new AccessDeniedException("Ariza qarorini faqat vakolatli administrator boshqara oladi");
        }

        EquipmentListingEntity equipment = equipmentRepository.findByIdForUpdate(app.getEquipmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Texnika topilmadi: " + app.getEquipmentId()));

        if (overrideRequest != null && overrideRequest.decision() != null) {
            if (partnerPort instanceof MockSandboxLeasingPartnerGateway sandbox) {
                sandbox.setSimulatedDecisionOverride(overrideRequest.decision().toUpperCase());
            }
        }

        PartnerSubmissionRequest partnerReq = new PartnerSubmissionRequest(
                app.getId(),
                app.getApplicationNumber(),
                equipment.getId(),
                equipment.getModelName(),
                app.getEstimatedEquipmentCost(),
                app.getProposedDownPayment(),
                app.getDesiredTermMonths(),
                app.getApplicantName(),
                app.getApplicantPhone(),
                app.getApplicantBusinessType(),
                app.getCurrency()
        );

        PartnerDecisionResult decision = partnerPort.submitForUnderwriting(partnerReq);
        applyPartnerDecision(app, decision, equipment);

        if (partnerPort instanceof MockSandboxLeasingPartnerGateway sandbox) {
            sandbox.reset();
        }

        return LeasingApplicationDto.fromEntity(app);
    }

    private void applyPartnerDecision(
            LeasingApplicationEntity app,
            PartnerDecisionResult decision,
            EquipmentListingEntity equipment
    ) {
        String fromStatus = app.getStatus();
        app.setPartnerBankCode(decision.partnerBankCode());
        app.setPartnerApplicationRef(decision.partnerApplicationRef());
        app.setIsSimulated(decision.isSimulated());
        app.setDisclaimer(decision.disclaimer());
        app.setReviewedAt(Instant.now());

        if (PartnerDecisionResult.DECISION_APPROVED.equalsIgnoreCase(decision.decision())) {
            app.setStatus("APPROVED");
            app.setApprovedPrincipal(decision.approvedPrincipal());
            app.setApprovedDownPayment(decision.approvedDownPayment());
            app.setApprovedTermMonths(decision.approvedTermMonths());
            app.setApprovedMonthlyInstallment(decision.approvedMonthlyInstallment());
            app.setReviewNotes(decision.reviewNotes());

            // Reserve equipment for this approved applicant
            equipment.setAvailabilityStatus("RESERVED");
            equipmentRepository.save(equipment);

            recordStatusHistory(app.getId(), fromStatus, "APPROVED", null, "PARTNER_BANK",
                    "Ariza hamkor bank tomonidan maʼqullandi (Oylik toʻlov: " + decision.approvedMonthlyInstallment() + " UZS)");
        } else if (PartnerDecisionResult.DECISION_REJECTED.equalsIgnoreCase(decision.decision())) {
            app.setStatus("REJECTED");
            app.setRejectionReason(decision.rejectionReason());
            app.setReviewNotes(decision.reviewNotes());

            recordStatusHistory(app.getId(), fromStatus, "REJECTED", null, "PARTNER_BANK",
                    "Ariza rad etildi: " + decision.rejectionReason());
        } else {
            app.setStatus("ADDITIONAL_INFO_REQUESTED");
            app.setReviewNotes(decision.reviewNotes());

            recordStatusHistory(app.getId(), fromStatus, "ADDITIONAL_INFO_REQUESTED", null, "PARTNER_BANK",
                    "Qoʻshimcha hujjatlar talab etiladi: " + decision.reviewNotes());
        }

        applicationRepository.save(app);
    }

    @Transactional(readOnly = true)
    public LeasingApplicationDto getApplication(UUID applicationId, UUID callerUserId, boolean isAdmin) {
        LeasingApplicationEntity app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Ariza topilmadi: " + applicationId));

        if (!app.getApplicantUserId().equals(callerUserId) && !isAdmin) {
            throw new AccessDeniedException("Boshqa foydalanuvchining arizasini koʻrish huquqi yoʻq (IDOR)");
        }
        return LeasingApplicationDto.fromEntity(app);
    }

    @Transactional(readOnly = true)
    public PageEnvelope<LeasingApplicationDto> listMyApplications(
            UUID applicantUserId,
            String status,
            Pageable pageable
    ) {
        Page<LeasingApplicationEntity> page;
        if (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL")) {
            page = applicationRepository.findByApplicantUserIdAndStatus(applicantUserId, status.toUpperCase().trim(), pageable);
        } else {
            page = applicationRepository.findByApplicantUserId(applicantUserId, pageable);
        }
        return PageEnvelope.from(page.map(LeasingApplicationDto::fromEntity));
    }

    @Transactional
    public LeasingApplicationDto withdrawApplication(
            UUID applicationId,
            UUID callerUserId,
            WithdrawApplicationRequest request,
            boolean isAdmin
    ) {
        LeasingApplicationEntity app = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Ariza topilmadi: " + applicationId));

        if (!app.getApplicantUserId().equals(callerUserId) && !isAdmin) {
            throw new AccessDeniedException("Faqat ariza muallifi arizani qaytarib olishi mumkin");
        }

        Set<String> withdrawableStatuses = Set.of("DRAFT", "SUBMITTED", "UNDER_REVIEW", "ADDITIONAL_INFO_REQUESTED", "APPROVED");
        if (!withdrawableStatuses.contains(app.getStatus())) {
            throw new BusinessConflictException("Ushbu holatdagi arizani qaytarib olib boʻlmaydi: " + app.getStatus());
        }

        String fromStatus = app.getStatus();
        app.setStatus("WITHDRAWN");
        app.setRejectionReason(request != null && request.reason() != null ? request.reason() : "Ariza muallifi tomonidan bekor qilindi");

        // Release reserved equipment if applicable
        EquipmentListingEntity equipment = equipmentRepository.findByIdForUpdate(app.getEquipmentId())
                .orElse(null);
        if (equipment != null && "RESERVED".equalsIgnoreCase(equipment.getAvailabilityStatus())) {
            equipment.setAvailabilityStatus("AVAILABLE");
            equipmentRepository.save(equipment);
        }

        LeasingApplicationEntity saved = applicationRepository.save(app);
        recordStatusHistory(saved.getId(), fromStatus, "WITHDRAWN", callerUserId, "APPLICANT", app.getRejectionReason());
        return LeasingApplicationDto.fromEntity(saved);
    }

    // =========================================================================
    // 3. Contracts & Repayment Schedules
    // =========================================================================

    @Transactional
    public LeasingContractDto prepareContract(UUID applicationId, UUID callerUserId, boolean isAdmin) {
        LeasingApplicationEntity app = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Ariza topilmadi: " + applicationId));

        if (!app.getApplicantUserId().equals(callerUserId) && !isAdmin) {
            throw new AccessDeniedException("Faqat tasdiqlangan ariza egasi shartnoma tayyorlashi mumkin");
        }

        if (!"APPROVED".equalsIgnoreCase(app.getStatus())) {
            throw new BusinessConflictException("Faqat maʼqullangan (APPROVED) arizalar uchun shartnoma tuzish mumkin (Joriy holat: " + app.getStatus() + ")");
        }

        // Lock equipment
        EquipmentListingEntity equipment = equipmentRepository.findByIdForUpdate(app.getEquipmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Texnika topilmadi: " + app.getEquipmentId()));

        // Check if there is already an active contract for this equipment
        if (contractRepository.existsByEquipmentIdAndStatusIn(equipment.getId(), ACTIVE_CONTRACT_STATUSES)) {
            throw new BusinessConflictException("Ushbu texnika uchun faol shartnoma allaqachon mavjud");
        }

        // Verify if contract was already prepared for this application
        var existingContract = contractRepository.findByApplicationId(app.getId());
        if (existingContract.isPresent()) {
            return LeasingContractDto.fromEntity(existingContract.get());
        }

        String contractNumber = generateContractNumber();
        BigDecimal principal = app.getApprovedPrincipal() != null ? app.getApprovedPrincipal() : app.getEstimatedEquipmentCost().subtract(app.getProposedDownPayment());
        BigDecimal downPayment = app.getApprovedDownPayment() != null ? app.getApprovedDownPayment() : app.getProposedDownPayment();
        int termMonths = app.getApprovedTermMonths() != null ? app.getApprovedTermMonths() : app.getDesiredTermMonths();
        BigDecimal annualRate = new BigDecimal("0.18"); // 18% annual

        BigDecimal termYears = BigDecimal.valueOf(termMonths).divide(BigDecimal.valueOf(12), 4, RoundingMode.HALF_UP);
        BigDecimal totalInterest = principal.multiply(annualRate).multiply(termYears).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalRepayment = principal.add(totalInterest);
        BigDecimal monthlyInstallment = totalRepayment.divide(BigDecimal.valueOf(termMonths), 2, RoundingMode.HALF_UP);

        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(termMonths);

        LeasingContractEntity contract = new LeasingContractEntity(
                contractNumber,
                app.getId(),
                equipment.getId(),
                app.getApplicantUserId(),
                equipment.getOwnerUserId(),
                app.getPartnerBankCode() != null ? app.getPartnerBankCode() : "MOCK_AGROBANK_SANDBOX",
                principal,
                downPayment,
                annualRate,
                termMonths,
                monthlyInstallment,
                totalRepayment,
                app.getCurrency(),
                startDate,
                endDate
        );

        contract.setDisclaimer("Ushbu lizing shartnomasi sinov simulyatsiyasi hisoblanadi (DEC-013). Huquqiy bank majburiyati emas.");
        LeasingContractEntity savedContract = contractRepository.save(contract);

        // Generate Repayment Schedule
        generateRepaymentSchedule(savedContract, termMonths, principal, totalInterest, monthlyInstallment, startDate);

        app.setStatus("CONTRACT_PENDING");
        applicationRepository.save(app);
        recordStatusHistory(app.getId(), "APPROVED", "CONTRACT_PENDING", callerUserId, "SYSTEM", "Lizing shartnomasi va toʻlov grafigi shakllantirildi: " + contractNumber);

        log.info("Prepared leasing contract: {} for application: {}", contractNumber, app.getApplicationNumber());
        return LeasingContractDto.fromEntity(savedContract);
    }

    private void generateRepaymentSchedule(
            LeasingContractEntity contract,
            int termMonths,
            BigDecimal totalPrincipal,
            BigDecimal totalInterest,
            BigDecimal monthlyInstallment,
            LocalDate startDate
    ) {
        BigDecimal principalPerMonth = totalPrincipal.divide(BigDecimal.valueOf(termMonths), 2, RoundingMode.HALF_UP);
        BigDecimal interestPerMonth = totalInterest.divide(BigDecimal.valueOf(termMonths), 2, RoundingMode.HALF_UP);

        List<LeasingRepaymentScheduleEntity> schedules = new ArrayList<>();
        for (int i = 1; i <= termMonths; i++) {
            LocalDate dueDate = startDate.plusMonths(i);
            BigDecimal currentPrincipal = (i == termMonths)
                    ? totalPrincipal.subtract(principalPerMonth.multiply(BigDecimal.valueOf(termMonths - 1)))
                    : principalPerMonth;
            BigDecimal currentInterest = (i == termMonths)
                    ? totalInterest.subtract(interestPerMonth.multiply(BigDecimal.valueOf(termMonths - 1)))
                    : interestPerMonth;
            BigDecimal currentTotal = currentPrincipal.add(currentInterest);

            schedules.add(new LeasingRepaymentScheduleEntity(
                    contract.getId(),
                    i,
                    dueDate,
                    currentPrincipal,
                    currentInterest,
                    currentTotal,
                    contract.getCurrency()
            ));
        }
        repaymentRepository.saveAll(schedules);
    }

    @Transactional
    public LeasingContractDto signContract(UUID contractId, UUID callerUserId, boolean isAdmin) {
        LeasingContractEntity contract = contractRepository.findByIdForUpdate(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Shartnoma topilmadi: " + contractId));

        if (!contract.getLesseeUserId().equals(callerUserId) && !isAdmin) {
            throw new AccessDeniedException("Faqat lizing oluvchi (lessee) shartnomani imzolashi mumkin");
        }

        if (!"PENDING_SIGNATURE".equalsIgnoreCase(contract.getStatus())) {
            throw new BusinessConflictException("Shartnoma allaqachon imzolangan yoki bekor qilingan (Holat: " + contract.getStatus() + ")");
        }

        // Lock equipment and update status to LEASED
        EquipmentListingEntity equipment = equipmentRepository.findByIdForUpdate(contract.getEquipmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Texnika topilmadi: " + contract.getEquipmentId()));

        equipment.setAvailabilityStatus("LEASED");
        equipmentRepository.save(equipment);

        contract.setStatus("ACTIVE");
        contract.setSignedAt(Instant.now());
        LeasingContractEntity saved = contractRepository.save(contract);

        // Update application
        LeasingApplicationEntity app = applicationRepository.findByIdForUpdate(contract.getApplicationId()).orElse(null);
        if (app != null) {
            app.setStatus("CONTRACT_SIGNED");
            applicationRepository.save(app);
            recordStatusHistory(app.getId(), "CONTRACT_PENDING", "CONTRACT_SIGNED", callerUserId, "APPLICANT",
                    "Shartnoma ikki tomonlama imzolandi va faollashtirildi: " + contract.getContractNumber());
        }

        log.info("Activated leasing contract: {} for equipment: {}", contract.getContractNumber(), equipment.getId());
        return LeasingContractDto.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public LeasingContractDto getContract(UUID contractId, UUID callerUserId, boolean isAdmin) {
        LeasingContractEntity contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Shartnoma topilmadi: " + contractId));

        if (!contract.getLesseeUserId().equals(callerUserId) && !contract.getLessorUserId().equals(callerUserId) && !isAdmin) {
            throw new AccessDeniedException("Ushbu shartnomani koʻrish huquqi yoʻq (IDOR)");
        }
        return LeasingContractDto.fromEntity(contract);
    }

    @Transactional(readOnly = true)
    public LeasingContractDto getContractByApplicationId(UUID applicationId, UUID callerUserId, boolean isAdmin) {
        LeasingApplicationEntity app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Ariza topilmadi: " + applicationId));

        if (!app.getApplicantUserId().equals(callerUserId) && !isAdmin) {
            throw new AccessDeniedException("Ushbu shartnomani koʻrish huquqi yoʻq (IDOR)");
        }

        LeasingContractEntity contract = contractRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Ariza boʻyicha tuzilgan shartnoma topilmadi: " + applicationId));

        return LeasingContractDto.fromEntity(contract);
    }

    @Transactional(readOnly = true)
    public List<RepaymentScheduleItemDto> getRepaymentSchedule(UUID contractId, UUID callerUserId, boolean isAdmin) {
        LeasingContractEntity contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Shartnoma topilmadi: " + contractId));

        if (!contract.getLesseeUserId().equals(callerUserId) && !contract.getLessorUserId().equals(callerUserId) && !isAdmin) {
            throw new AccessDeniedException("Toʻlov grafigini koʻrish huquqi yoʻq (IDOR)");
        }

        List<LeasingRepaymentScheduleEntity> schedules = repaymentRepository.findByContractIdOrderByInstallmentNumberAsc(contractId);
        return schedules.stream().map(RepaymentScheduleItemDto::fromEntity).toList();
    }

    // =========================================================================
    // 4. Helper Utilities
    // =========================================================================

    private void recordStatusHistory(
            UUID applicationId,
            String fromStatus,
            String toStatus,
            UUID actorUserId,
            String actorRole,
            String notes
    ) {
        historyRepository.save(new LeasingApplicationStatusHistoryEntity(
                applicationId,
                fromStatus,
                toStatus,
                actorUserId,
                actorRole,
                notes
        ));
    }

    private String generateApplicationNumber() {
        int year = LocalDate.now().getYear();
        int random = ThreadLocalRandom.current().nextInt(10000, 99999);
        return "LAP-" + year + "-" + random;
    }

    private String generateContractNumber() {
        int year = LocalDate.now().getYear();
        int random = ThreadLocalRandom.current().nextInt(10000, 99999);
        return "LCT-" + year + "-" + random;
    }
}
