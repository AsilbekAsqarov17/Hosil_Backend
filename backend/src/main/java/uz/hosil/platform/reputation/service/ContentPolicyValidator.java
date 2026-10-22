package uz.hosil.platform.reputation.service;

import org.springframework.stereotype.Component;
import uz.hosil.platform.common.error.ContentPolicyViolationException;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Automated content policy validator per docs/12_reputation_and_reviews.md §2.1 & §8.
 * Detects phone numbers, passport sequences, and prohibited abusive phrases.
 */
@Component
public class ContentPolicyValidator {

    private static final Pattern PHONE_PATTERN = Pattern.compile("(\\+?998\\s?\\d{2}\\s?\\d{3}\\s?\\d{2}\\s?\\d{2}|\\b\\d{9,12}\\b)");
    private static final Pattern PASSPORT_PATTERN = Pattern.compile("\\b[A-Za-z]{2}\\s?\\d{7}\\b");

    private static final List<String> PROHIBITED_WORDS = List.of(
            "haromxoʻr", "haromxor", "firibgar", "muttaham", "it", "scam", "lox", "ahmoq", "padarlaʼnat"
    );

    public void validateText(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            return;
        }

        // PII Check: Phone numbers
        if (PHONE_PATTERN.matcher(text).find()) {
            throw new ContentPolicyViolationException(
                    "Matnda shaxsiy telefon raqamini koʻrsatish qatʼiyan man etiladi (" + fieldName + ")"
            );
        }

        // PII Check: Passport sequences
        if (PASSPORT_PATTERN.matcher(text).find()) {
            throw new ContentPolicyViolationException(
                    "Matnda shaxsiy pasport maʼlumotlarini koʻrsatish qatʼiyan man etiladi (" + fieldName + ")"
            );
        }

        // Abuse/Profanity Check
        String lower = text.toLowerCase();
        for (String word : PROHIBITED_WORDS) {
            if (lower.contains(word)) {
                throw new ContentPolicyViolationException(
                        "Matn platforma muloqot madaniyati qoidalariga zid soʻzlarni oʻz ichiga oladi: '" + word + "'"
                );
            }
        }
    }
}
