package com.example.analyzelog.web;

import com.example.analyzelog.model.StaticRefererEntry;
import com.example.analyzelog.model.StaticUaEntry;
import com.example.analyzelog.model.NoiseFilterEntry;
import com.example.analyzelog.service.AdminService;
import com.example.analyzelog.util.StringUtils;
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

    private static final String FLASH_MESSAGE   = "flashMessage";
    private static final String FLASH_ERROR     = "flashError";
    private static final String REDIRECT_ADMIN  = "redirect:/admin";
    private static final String ERR_ADD         = "Failed to add: ";
    private static final String ERR_UPDATE      = "Failed to update: ";
    private static final String ERR_DELETE      = "Failed to delete: ";

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
                    StringUtils.nullIfBlank(pattern), sortOrder));
            ra.addFlashAttribute(FLASH_MESSAGE, "User agent '%s' added.".formatted(uaName));
        } catch (Exception e) {
            ra.addFlashAttribute(FLASH_ERROR, ERR_ADD + e.getMessage());
        }
        return REDIRECT_ADMIN;
    }

    @PostMapping("/ua/update-labels")
    public String updateUaLabels(@RequestParam String uaName,
                                 @RequestParam String uaGroup,
                                 @RequestParam String uaLabel,
                                 RedirectAttributes ra) {
        try {
            adminService.updateUaLabels(uaName, uaGroup, uaLabel);
            ra.addFlashAttribute(FLASH_MESSAGE, "User agent '%s' updated.".formatted(uaName));
        } catch (Exception e) {
            ra.addFlashAttribute(FLASH_ERROR, ERR_UPDATE + e.getMessage());
        }
        return REDIRECT_ADMIN;
    }

    @PostMapping("/ua/update-classifier")
    public String updateUaClassifier(@RequestParam String uaName,
                                     @RequestParam(required = false) String pattern,
                                     @RequestParam(required = false) Integer sortOrder,
                                     RedirectAttributes ra) {
        try {
            adminService.updateUaClassifier(uaName, StringUtils.nullIfBlank(pattern), sortOrder);
            ra.addFlashAttribute(FLASH_MESSAGE, "Classifier for '%s' updated.".formatted(uaName));
        } catch (Exception e) {
            ra.addFlashAttribute(FLASH_ERROR, ERR_UPDATE + e.getMessage());
        }
        return REDIRECT_ADMIN;
    }

    @PostMapping("/ua/delete")
    public String deleteUa(@RequestParam String uaName, RedirectAttributes ra) {
        try {
            adminService.deleteUa(uaName);
            ra.addFlashAttribute(FLASH_MESSAGE, "User agent '%s' deleted.".formatted(uaName));
        } catch (Exception e) {
            ra.addFlashAttribute(FLASH_ERROR, ERR_DELETE + e.getMessage());
        }
        return REDIRECT_ADMIN;
    }

    @PostMapping("/referer/add")
    public String addReferer(@RequestParam String label,
                             @RequestParam(required = false) String domain,
                             @RequestParam(required = false) String domainStartsWith,
                             @RequestParam(required = false) String domainEndsWith,
                             RedirectAttributes ra) {
        try {
            adminService.addReferer(new StaticRefererEntry(0, label, domain, domainStartsWith, domainEndsWith));
            ra.addFlashAttribute(FLASH_MESSAGE, "Referer rule '%s' added.".formatted(label));
        } catch (Exception e) {
            ra.addFlashAttribute(FLASH_ERROR, ERR_ADD + e.getMessage());
        }
        return REDIRECT_ADMIN;
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
            ra.addFlashAttribute(FLASH_MESSAGE, "Referer rule '%s' updated.".formatted(label));
        } catch (Exception e) {
            ra.addFlashAttribute(FLASH_ERROR, ERR_UPDATE + e.getMessage());
        }
        return REDIRECT_ADMIN;
    }

    @PostMapping("/referer/delete")
    public String deleteReferer(@RequestParam long id, RedirectAttributes ra) {
        try {
            adminService.deleteReferer(id);
            ra.addFlashAttribute(FLASH_MESSAGE, "Referer rule deleted.");
        } catch (Exception e) {
            ra.addFlashAttribute(FLASH_ERROR, ERR_DELETE + e.getMessage());
        }
        return REDIRECT_ADMIN;
    }

    @PostMapping("/noise/add")
    public String addNoiseRule(@RequestParam String uaName,
                               @RequestParam String uriStem,
                               RedirectAttributes ra) {
        try {
            adminService.addNoiseRule(new NoiseFilterEntry(uaName, uriStem));
            ra.addFlashAttribute(FLASH_MESSAGE, "Noise rule '%s %s' added.".formatted(uaName, uriStem));
        } catch (Exception e) {
            ra.addFlashAttribute(FLASH_ERROR, ERR_ADD + e.getMessage());
        }
        return REDIRECT_ADMIN;
    }

    @PostMapping("/noise/delete")
    public String deleteNoiseRule(@RequestParam String uaName,
                                  @RequestParam String uriStem,
                                  RedirectAttributes ra) {
        try {
            adminService.deleteNoiseRule(uaName, uriStem);
            ra.addFlashAttribute(FLASH_MESSAGE, "Noise rule '%s %s' deleted.".formatted(uaName, uriStem));
        } catch (Exception e) {
            ra.addFlashAttribute(FLASH_ERROR, ERR_DELETE + e.getMessage());
        }
        return REDIRECT_ADMIN;
    }

    @PostMapping("/reload")
    public String reload(RedirectAttributes ra) {
        adminService.reloadConfiguration();
        ra.addFlashAttribute(FLASH_MESSAGE, "Configuration reloaded from database.");
        return REDIRECT_ADMIN;
    }

    @PostMapping("/reclassify")
    public String reclassify(RedirectAttributes ra) {
        try {
            int count = adminService.reclassifyLogs();
            ra.addFlashAttribute(FLASH_MESSAGE,
                    "Re-classified %d distinct user agents in cloudfront_logs.".formatted(count));
        } catch (Exception e) {
            ra.addFlashAttribute(FLASH_ERROR, "Re-classification failed: " + e.getMessage());
        }
        return REDIRECT_ADMIN;
    }

}
