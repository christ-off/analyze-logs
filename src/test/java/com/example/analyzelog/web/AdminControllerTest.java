package com.example.analyzelog.web;

import com.example.analyzelog.model.NoiseFilterEntry;
import com.example.analyzelog.model.StaticRefererEntry;
import com.example.analyzelog.model.StaticUaEntry;
import com.example.analyzelog.service.AdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(AdminController.class)
class AdminControllerTest {

    @Autowired MockMvcTester mvc;
    @MockitoBean AdminService adminService;

    // ── GET /admin ────────────────────────────────────────────────────────────

    @Test
    void get_admin_returnsAdminView() {
        when(adminService.allUa()).thenReturn(List.of());
        when(adminService.allReferers()).thenReturn(List.of());
        when(adminService.allNoiseRules()).thenReturn(List.of());

        assertThat(mvc.get().uri("/admin").exchange())
                .hasStatusOk()
                .hasViewName("admin");
    }

    // ── UA endpoints ──────────────────────────────────────────────────────────

    @Test
    void post_addUa_redirectsToAdmin() {
        assertThat(mvc.post().uri("/admin/ua/add").with(csrf())
                        .param("uaName", "TestBot")
                        .param("uaGroup", "AI Bots")
                        .param("uaLabel", "Test Bot")
                        .exchange())
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/admin");

        verify(adminService).addUa(any(StaticUaEntry.class));
    }

    @Test
    void post_addUa_onError_redirectsToAdmin() {
        doThrow(new RuntimeException("duplicate key")).when(adminService).addUa(any());

        assertThat(mvc.post().uri("/admin/ua/add").with(csrf())
                        .param("uaName", "TestBot")
                        .param("uaGroup", "AI Bots")
                        .param("uaLabel", "Test Bot")
                        .exchange())
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/admin");
    }

    @Test
    void post_updateUaLabels_redirectsToAdmin() {
        assertThat(mvc.post().uri("/admin/ua/update-labels").with(csrf())
                        .param("uaName", "TestBot")
                        .param("uaGroup", "Search Bots")
                        .param("uaLabel", "Test Bot v2")
                        .exchange())
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/admin");

        verify(adminService).updateUaLabels("TestBot", "Search Bots", "Test Bot v2");
    }

    @Test
    void post_updateUaLabels_onError_redirectsToAdmin() {
        doThrow(new RuntimeException("not found")).when(adminService).updateUaLabels(anyString(), anyString(), anyString());

        assertThat(mvc.post().uri("/admin/ua/update-labels").with(csrf())
                        .param("uaName", "TestBot")
                        .param("uaGroup", "Search Bots")
                        .param("uaLabel", "Test Bot v2")
                        .exchange())
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/admin");
    }

    @Test
    void post_updateUaClassifier_redirectsToAdmin() {
        assertThat(mvc.post().uri("/admin/ua/update-classifier").with(csrf())
                        .param("uaName", "TestBot")
                        .param("pattern", "TestBot/")
                        .param("sortOrder", "99")
                        .exchange())
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/admin");

        verify(adminService).updateUaClassifier("TestBot", "TestBot/", 99);
    }

    @Test
    void post_updateUaClassifier_onError_redirectsToAdmin() {
        doThrow(new RuntimeException("db error")).when(adminService).updateUaClassifier(anyString(), any(), any());

        assertThat(mvc.post().uri("/admin/ua/update-classifier").with(csrf())
                        .param("uaName", "TestBot")
                        .exchange())
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/admin");
    }

    @Test
    void post_deleteUa_redirectsToAdmin() {
        assertThat(mvc.post().uri("/admin/ua/delete").with(csrf())
                        .param("uaName", "TestBot")
                        .exchange())
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/admin");

        verify(adminService).deleteUa("TestBot");
    }

    @Test
    void post_deleteUa_onError_redirectsToAdmin() {
        doThrow(new RuntimeException("constraint violation")).when(adminService).deleteUa(anyString());

        assertThat(mvc.post().uri("/admin/ua/delete").with(csrf())
                        .param("uaName", "TestBot")
                        .exchange())
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/admin");
    }

    // ── Referer endpoints ─────────────────────────────────────────────────────

    @Test
    void post_addReferer_redirectsToAdmin() {
        assertThat(mvc.post().uri("/admin/referer/add").with(csrf())
                        .param("label", "Google")
                        .param("domainStartsWith", "google.")
                        .exchange())
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/admin");

        verify(adminService).addReferer(any(StaticRefererEntry.class));
    }

    @Test
    void post_addReferer_onError_redirectsToAdmin() {
        doThrow(new RuntimeException("duplicate")).when(adminService).addReferer(any());

        assertThat(mvc.post().uri("/admin/referer/add").with(csrf())
                        .param("label", "Google")
                        .exchange())
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/admin");
    }

    @Test
    void post_updateReferer_redirectsToAdmin() {
        assertThat(mvc.post().uri("/admin/referer/update").with(csrf())
                        .param("id", "5")
                        .param("label", "Google")
                        .param("domainStartsWith", "google.")
                        .exchange())
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/admin");

        verify(adminService).updateReferer(any(StaticRefererEntry.class));
    }

    @Test
    void post_updateReferer_onError_redirectsToAdmin() {
        doThrow(new RuntimeException("not found")).when(adminService).updateReferer(any());

        assertThat(mvc.post().uri("/admin/referer/update").with(csrf())
                        .param("id", "5")
                        .param("label", "Google")
                        .exchange())
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/admin");
    }

    @Test
    void post_deleteReferer_redirectsToAdmin() {
        assertThat(mvc.post().uri("/admin/referer/delete").with(csrf())
                        .param("id", "3")
                        .exchange())
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/admin");

        verify(adminService).deleteReferer(3L);
    }

    @Test
    void post_deleteReferer_onError_redirectsToAdmin() {
        doThrow(new RuntimeException("not found")).when(adminService).deleteReferer(anyLong());

        assertThat(mvc.post().uri("/admin/referer/delete").with(csrf())
                        .param("id", "3")
                        .exchange())
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/admin");
    }

    // ── Noise filter endpoints ────────────────────────────────────────────────

    @Test
    void post_addNoiseRule_redirectsToAdmin() {
        assertThat(mvc.post().uri("/admin/noise/add").with(csrf())
                        .param("uaName", "TestBot")
                        .param("uriStem", "/wp-login.php")
                        .exchange())
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/admin");

        verify(adminService).addNoiseRule(any(NoiseFilterEntry.class));
    }

    @Test
    void post_addNoiseRule_onError_redirectsToAdmin() {
        doThrow(new RuntimeException("duplicate")).when(adminService).addNoiseRule(any());

        assertThat(mvc.post().uri("/admin/noise/add").with(csrf())
                        .param("uaName", "TestBot")
                        .param("uriStem", "/wp-login.php")
                        .exchange())
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/admin");
    }

    @Test
    void post_deleteNoiseRule_redirectsToAdmin() {
        assertThat(mvc.post().uri("/admin/noise/delete").with(csrf())
                        .param("uaName", "TestBot")
                        .param("uriStem", "/wp-login.php")
                        .exchange())
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/admin");

        verify(adminService).deleteNoiseRule("TestBot", "/wp-login.php");
    }

    @Test
    void post_deleteNoiseRule_onError_redirectsToAdmin() {
        doThrow(new RuntimeException("not found")).when(adminService).deleteNoiseRule(anyString(), anyString());

        assertThat(mvc.post().uri("/admin/noise/delete").with(csrf())
                        .param("uaName", "TestBot")
                        .param("uriStem", "/wp-login.php")
                        .exchange())
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/admin");
    }

    // ── Reload / reclassify ───────────────────────────────────────────────────

    @Test
    void post_reload_reloadsAndRedirects() {
        assertThat(mvc.post().uri("/admin/reload").with(csrf()).exchange())
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/admin");

        verify(adminService).reloadConfiguration();
    }

    @Test
    void post_reclassify_reclassifiesAndRedirects() {
        when(adminService.reclassifyLogs()).thenReturn(42);

        assertThat(mvc.post().uri("/admin/reclassify").with(csrf()).exchange())
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/admin");

        verify(adminService).reclassifyLogs();
    }

    @Test
    void post_reclassify_onError_redirectsToAdmin() {
        doThrow(new RuntimeException("db error")).when(adminService).reclassifyLogs();

        assertThat(mvc.post().uri("/admin/reclassify").with(csrf()).exchange())
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/admin");
    }
}
