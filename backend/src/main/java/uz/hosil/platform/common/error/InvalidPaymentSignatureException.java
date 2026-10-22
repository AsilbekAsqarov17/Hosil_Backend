package uz.hosil.platform.common.error;

/**
 * Thrown when an incoming payment provider webhook signature verification fails (PAY-004).
 */
public class InvalidPaymentSignatureException extends RuntimeException {

    public InvalidPaymentSignatureException(String message) {
        super(message);
    }
}
