package uz.hosil.platform.identity.web;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthTokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        int expiresInSeconds,
        Boolean isNewUser,
        UserSummaryDto user
) {
    public static AuthTokenResponse ofRefresh(String accessToken, String refreshToken, String tokenType, int expiresInSeconds) {
        return new AuthTokenResponse(accessToken, refreshToken, tokenType, expiresInSeconds, null, null);
    }
}
