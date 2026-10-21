package uz.hosil.platform.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Standard unified error response envelope conforming to RFC 7807 principles
 * and docs/05_api_contract.md §2.3.
 * Supports dual-compatibility:
 * 1. Nested `error: {...}` object per docs/05_api_contract.md §2.3.
 * 2. Flat top-level attributes (`code`, `message`, `details`, etc.) for seamless
 *    backward-compatibility across all REST test suites and mobile client versions.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorEnvelope(
    String code,
    String message,
    List<String> details,
    Instant timestamp,
    String path,
    String traceId,
    ErrorDetail error
) {
    public record ErrorDetail(
        String code,
        String message,
        List<String> details,
        Instant timestamp,
        String path,
        String correlationId
    ) {}

    public ErrorEnvelope(String code, String message, List<String> details, Instant timestamp, String path, String traceId) {
        this(code, message, details != null ? details : List.of(), timestamp, path, traceId,
                new ErrorDetail(code, message, details != null ? details : List.of(), timestamp, path, traceId));
    }

    public static ErrorEnvelope of(String code, String message, String path) {
        Instant now = Instant.now();
        ErrorDetail detail = new ErrorDetail(code, message, List.of(), now, path, null);
        return new ErrorEnvelope(code, message, List.of(), now, path, null, detail);
    }

    public static ErrorEnvelope of(String code, String message, List<String> details, String path) {
        Instant now = Instant.now();
        List<String> nonNullDetails = details != null ? details : List.of();
        ErrorDetail detail = new ErrorDetail(code, message, nonNullDetails, now, path, null);
        return new ErrorEnvelope(code, message, nonNullDetails, now, path, null, detail);
    }
}
