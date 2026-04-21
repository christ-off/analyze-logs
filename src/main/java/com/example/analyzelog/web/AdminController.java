package com.example.analyzelog.web;

import com.example.analyzelog.model.StaticRefererEntry;
import com.example.analyzelog.model.StaticUaEntry;
import com.example.analyzelog.model.NoiseFilterEntry;
import com.example.analyzelog.service.AdminService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    public String admin(Model model) {
        model.addAttribute("userAgents", adminService.allUa());
        model.addAttribute("refererRules", adminService.allReferers());
        model.addAttribute("noiseRules", adminService.allNoiseRules());
        return "admin";
    }

    @PostMapping("/ua/add")
    public String addUa(@RequestParam String uaName,
                        @RequestParam String uaGroup,
                        @RequestParam String uaLabel,
                        @RequestParam(required = false) String pattern,
                        @RequestParam(required = false) Integer sortOrder,
                        RedirectAttributes ra) {
        try {
            adminService.addUa(new StaticUaEntry(uaName, uaGroup, uaLabel,
                    nullIfBlank(pattern), sortOrder));
            ra.addFlashAttribute("flashMessage", "User agent '%s' added.".formatted(uaName));
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Failed to add: " + e.getMessage());
        }
        return "redirect:/admin";
    }

    @PostMapping("/ua/update-labels")
    public String updateUaLabels(@RequestParam String uaName,
                                 @RequestParam String uaGroup,
                                 @RequestParam String uaLabel,
                                 RedirectAttributes ra) {
        try {
            adminService.updateUaLabels(uaName, uaGroup, uaLabel);
            ra.addFlashAttribute("flashMessage", "User agent '%s' updated.".formatted(uaName));
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Failed to update: " + e.getMessage());
        }
        return "redirect:/admin";
    }

    @PostMapping("/ua/update-classifier")
    public String updateUaClassifier(@RequestParam String uaName,
                                     @RequestParam(required = false) String pattern,
                                     @RequestParam(required = false) Integer sortOrder,
                                     RedirectAttributes ra) {
        try {
            adminService.updateUaClassifier(uaName, nullIfBlank(pattern), sortOrder);
            ra.addFlashAttribute("flashMessage", "Classifier for '%s' updated.".formatted(uaName));
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Failed to update: " + e.getMessage());
        }
        return "redirect:/admin";
    }

    @PostMapping("/ua/delete")
    public String deleteUa(@RequestParam String uaName, RedirectAttributes ra) {
        try {
            adminService.deleteUa(uaName);
            ra.addFlashAttribute("flashMessage", "User agent '%s' deleted.".formatted(uaName));
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Failed to delete: " + e.getMessage());
        }
        return "redirect:/admin";
    }

    @PostMapping("/referer/add")
    public String addReferer(@RequestParam String label,
                             @RequestParam(required = false) String domain,
                             @RequestParam(required = false) String domainStartsWith,
                             @RequestParam(required = false) String domainEndsWith,
                             RedirectAttributes ra) {
        try {
            adminService.addReferer(new StaticRefererEntry(0, label, domain, domainStartsWith, domainEndsWith));
            ra.addFlashAttribute("flashMessage", "Referer rule '%s' added.".formatted(label));
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Failed to add: " + e.getMessage());
        }
        return "redirect:/admin";
    }

    @PostMapping("/referer/update")
    public String updateReferer(@RequestParam long id,
                                @RequestParam String label,
                                @RequestParam(required = false) String domain,
                                @RequestParam(required = false) String domainStartsWith,
                                @RequestParam(required = false) String domainEndsWith,
                                RedirectAttributes ra) {
        try {
            adminService.updateReferer(new StaticRefererEntry(id, label, domain, domainStartsWith, domainEndsWith));
            ra.addFlashAttribute("flashMessage", "Referer rule '%s' updated.".formatted(label));
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Failed to update: " + e.getMessage());
        }
        return "redirect:/admin";
    }

    @PostMapping("/referer/delete")
    public String deleteReferer(@RequestParam long id, RedirectAttributes ra) {
        try {
            adminService.deleteReferer(id);
            ra.addFlashAttribute("flashMessage", "Referer rule deleted.");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Failed to delete: " + e.getMessage());
        }
        return "redirect:/admin";
    }

    @PostMapping("/noise/add")
    public String addNoiseRule(@RequestParam String uaName,
                               @RequestParam String uriStem,
                               RedirectAttributes ra) {
        try {
            adminService.addNoiseRule(new NoiseFilterEntry(uaName, uriStem));
            ra.addFlashAttribute("flashMessage", "Noise rule '%s %s' added.".formatted(uaName, uriStem));
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Failed to add: " + e.getMessage());
        }
        return "redirect:/admin";
    }

    @PostMapping("/noise/delete")
    public String deleteNoiseRule(@RequestParam String uaName,
                                  @RequestParam String uriStem,
                                  RedirectAttributes ra) {
        try {
            adminService.deleteNoiseRule(uaName, uriStem);
            ra.addFlashAttribute("flashMessage", "Noise rule '%s %s' deleted.".formatted(uaName, uriStem));
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Failed to delete: " + e.getMessage());
        }
        return "redirect:/admin";
    }

    @PostMapping("/reload")
    public String reload(RedirectAttributes ra) {
        adminService.reloadConfiguration();
        ra.addFlashAttribute("flashMessage", "Configuration reloaded from database.");
        return "redirect:/admin";
    }

    @PostMapping("/reclassify")
    public String reclassify(RedirectAttributes ra) {
        try {
            int count = adminService.reclassifyLogs();
            ra.addFlashAttribute("flashMessage",
                    "Re-classified %d distinct user agents in cloudfront_logs.".formatted(count));
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Re-classification failed: " + e.getMessage());
        }
        return "redirect:/admin";
    }

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
