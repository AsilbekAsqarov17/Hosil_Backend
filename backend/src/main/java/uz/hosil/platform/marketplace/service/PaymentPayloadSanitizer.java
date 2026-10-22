package uz.hosil.platform.marketplace.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sanitizes sensitive personal and financial data (PAN, CVV, passwords, tokens)
 * from payloads before logging and audit persistence (ZRU-547, PCI-DSS, DEC-007).
 */
public final class PaymentPayloadSanitizer {

    private PaymentPayloadSanitizer() {
    }

    // Matches JSON fields containing sensitive keys: "pan", "cardNumber", "card_number", "cvv", "cvc", "secret", "token", "password"
    private static final Pattern SENSITIVE_JSON_FIELD_PATTERN = Pattern.compile(
            "(\"(?:pan|card_?number|cvv|cvc|secret|token|password|accessToken|refreshToken)\"\\s*:\\s*)\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE
    );

    // Matches raw credit card PAN numbers (13 to 19 digits, optionally separated by dashes or spaces)
    private static final Pattern CARD_NUMBER_PATTERN = Pattern.compile(
            "\\b(?:\\d[ -]*?){13,19}\\b"
    );

    public static String sanitize(String payload) {
        if (payload == null || payload.isBlank()) {
            return payload;
        }

        // 1. Redact sensitive JSON key-value pairs
        Matcher jsonMatcher = SENSITIVE_JSON_FIELD_PATTERN.matcher(payload);
        StringBuffer sb = new StringBuffer();
        while (jsonMatcher.find()) {
            String keyPrefix = jsonMatcher.group(1);
            String rawValue = jsonMatcher.group(2);
            String maskedValue;
            if (rawValue.length() >= 4) {
                maskedValue = "***" + rawValue.substring(rawValue.length() - 4);
            } else {
                maskedValue = "***";
            }
            jsonMatcher.appendReplacement(sb, Matcher.quoteReplacement(keyPrefix + "\"" + maskedValue + "\""));
        }
        jsonMatcher.appendTail(sb);
        String step1 = sb.toString();

        // 2. Redact any remaining standalone credit card numbers
        Matcher cardMatcher = CARD_NUMBER_PATTERN.matcher(step1);
        StringBuffer sb2 = new StringBuffer();
        while (cardMatcher.find()) {
            String digits = cardMatcher.group().replaceAll("[^0-9]", "");
            if (digits.length() >= 13 && digits.length() <= 19) {
                String masked = "****-****-****-" + digits.substring(digits.length() - 4);
                cardMatcher.appendReplacement(sb2, Matcher.quoteReplacement(masked));
            } else {
                cardMatcher.appendReplacement(sb2, Matcher.quoteReplacement(cardMatcher.group()));
            }
        }
        cardMatcher.appendTail(sb2);

        return sb2.toString();
    }
}
