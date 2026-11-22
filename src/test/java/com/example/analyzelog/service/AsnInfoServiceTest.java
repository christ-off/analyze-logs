package com.example.analyzelog.service;

import com.example.analyzelog.model.AsnInfo;
import com.example.analyzelog.repository.AsnInfoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsnInfoServiceTest {

    private static List<AsnInfo> parse(String json) {
        return AsnInfoService.parseJson(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    // Offline stand-in for the download: serves the given JSON body for any URI.
    private static RestClient restClientReturning(String json) {
        return RestClient.builder().requestFactory((uri, method) -> new StubRequest(json)).build();
    }

    private static final class StubRequest implements ClientHttpRequest {
        private final String json;
        private final HttpHeaders headers = new HttpHeaders();
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();

        private StubRequest(String json) {
            this.json = json;
        }

        @Override public HttpMethod getMethod() { return HttpMethod.GET; }
        @Override public URI getURI() { return URI.create(AsnInfoService.JSON_URL); }
        @Override public Map<String, Object> getAttributes() { return new HashMap<>(); }
        @Override public HttpHeaders getHeaders() { return headers; }
        @Override public OutputStream getBody() { return body; }
        @Override public ClientHttpResponse execute() { return new StubResponse(json); }
    }

    private record StubResponse(String json) implements ClientHttpResponse {
        @Override public HttpStatusCode getStatusCode() { return HttpStatus.OK; }
        @Override public String getStatusText() { return "OK"; }
        @Override public HttpHeaders getHeaders() { return new HttpHeaders(); }
        @Override public InputStream getBody() {
            return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        }
        @Override public void close() { /* No resources to close: the body is an in-memory stream. */ }
    }

    @Test
    void parsesNameCountryAndCategory() {
        List<AsnInfo> result = parse("""
                [{"asn": 24940, "metadata": {"handle": "HETZNER-AS", "description": "Hetzner Online GmbH",
                   "countryCode": "DE", "category": "hosting"}, "stats": null},
                 {"asn": 16276, "metadata": {"handle": "OVH", "description": "OVH SAS",
                   "countryCode": "FR", "category": "isp"}}]""");

        assertEquals(2, result.size());
        assertEquals(new AsnInfo("24940", "HETZNER-AS", "Hetzner Online GmbH", "DE", "hosting"), result.get(0));
        assertEquals("isp", result.get(1).category());
    }

    @Test
    void missingOrNullFieldsBecomeNull() {
        List<AsnInfo> result = parse("""
                [{"asn": 99, "metadata": {"handle": "", "description": null, "category": null}},
                 {"asn": 100}]""");

        assertEquals(2, result.size());
        assertNull(result.get(0).handle());
        assertNull(result.get(0).name());
        assertNull(result.get(0).countryCode());
        assertNull(result.get(0).category());
        assertNull(result.get(1).name());
    }

    @Test
    void ignoresEntriesWithoutNumericAsn() {
        assertTrue(parse("[{\"asn\": \"x\", \"metadata\": {}}, {\"metadata\": {}}]").isEmpty());
    }

    @Test
    void rejectsNonArrayPayload() {
        assertThrows(IllegalStateException.class, () -> parse("{\"asn\": 1}"));
    }

    @Test
    void refresh_downloadsParsesAndReplacesTheStoredNames() {
        AsnInfoRepository repository = mock(AsnInfoRepository.class);
        AsnInfoService service = new AsnInfoService(restClientReturning("""
                [{"asn": 24940, "metadata": {"handle": "HETZNER-AS", "description": "Hetzner Online GmbH",
                   "countryCode": "DE", "category": "hosting"}},
                 {"asn": 16276, "metadata": {"handle": "OVH", "description": "OVH SAS",
                   "countryCode": "FR", "category": "isp"}}]"""), repository);

        assertEquals(2, service.refresh());

        verify(repository).replaceAll(List.of(
                new AsnInfo("24940", "HETZNER-AS", "Hetzner Online GmbH", "DE", "hosting"),
                new AsnInfo("16276", "OVH", "OVH SAS", "FR", "isp")));
    }

    @Test
    void refresh_throwsAndKeepsStoredNamesWhenTheArrayIsEmpty() {
        AsnInfoRepository repository = mock(AsnInfoRepository.class);
        AsnInfoService service = new AsnInfoService(restClientReturning("[]"), repository);

        IllegalStateException e = assertThrows(IllegalStateException.class, service::refresh);
        assertTrue(e.getMessage().contains(AsnInfoService.JSON_URL));
        verify(repository, never()).replaceAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void count_delegatesToTheRepository() {
        AsnInfoRepository repository = mock(AsnInfoRepository.class);
        when(repository.count()).thenReturn(42);

        assertEquals(42, new AsnInfoService(restClientReturning("[]"), repository).count());
    }
}
