package uz.hosil.platform.logistics.service;

/**
 * Service Provider Interface (SPI) for partner bank equipment leasing and underwriting.
 * Ensures strict decoupling from physical banking institutions per PRD LEASE-004 and DEC-013.
 */
public interface LeasingPartnerPort {

    /**
     * Submits an applicant's leasing application for partner credit and risk assessment.
     */
    PartnerDecisionResult submitForUnderwriting(PartnerSubmissionRequest request);

    /**
     * Inquires the real-time status of an underwriting application from the partner.
     */
    PartnerDecisionResult queryUnderwritingStatus(String partnerApplicationRef);

    /**
     * Unique code identifying the active financing partner.
     */
    String getPartnerCode();

    /**
     * Display name of the active financing partner.
     */
    String getPartnerName();
}
