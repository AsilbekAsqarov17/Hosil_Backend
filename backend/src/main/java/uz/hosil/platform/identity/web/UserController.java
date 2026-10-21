package uz.hosil.platform.identity.web;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.hosil.platform.common.security.UserPrincipal;
import uz.hosil.platform.identity.service.AuthService;

/**
 * Controller for user profile management.
 * Protected by JwtAuthenticationFilter on /api/**.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserSummaryDto> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        UserSummaryDto userSummary = authService.getCurrentUserSummary(principal.userId());
        return ResponseEntity.ok(userSummary);
    }
}
