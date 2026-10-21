package uz.hosil.platform.admin.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller for back-office admin operations dashboard.
 * Server-rendered via Thymeleaf per ADR-002 and docs/design/05_admin_panel_ui.md.
 */
@Controller
public class AdminDashboardController {

    @GetMapping({"/admin", "/admin/dashboard"})
    public String dashboard(Model model) {
        model.addAttribute("appName", "Hosil Admin");
        model.addAttribute("pendingVerificationsCount", 4);
        model.addAttribute("pendingListingsCount", 12);
        model.addAttribute("flaggedReviewsCount", 3);
        model.addAttribute("pendingCarriersCount", 2);
        return "admin/dashboard";
    }
}

