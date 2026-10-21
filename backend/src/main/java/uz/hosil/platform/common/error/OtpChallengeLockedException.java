package uz.hosil.platform.common.error;

public class OtpChallengeLockedException extends RuntimeException {
    public OtpChallengeLockedException(String message) {
        super(message);
    }
}
