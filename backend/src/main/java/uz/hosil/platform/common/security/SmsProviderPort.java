package uz.hosil.platform.common.security;

/**
 * Replaceable adapter port for SMS delivery per DEC-003.
 * Preserves provider neutrality until a commercial SMS gateway is selected.
 * Extends OtpDeliveryPort for unified delivery abstraction.
 */
public interface SmsProviderPort extends OtpDeliveryPort {
}
