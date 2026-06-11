package com.example.analyzelog.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IpInfoServiceTest {

    @Mock
    RestClient restClient;

    @Mock
    RestClient.RequestHeadersUriSpec<?> uriSpec;

    @Mock
    RestClient.RequestHeadersSpec<?> headersSpec;

    @Mock
    RestClient.ResponseSpec responseSpec;

    IpInfoService service;

    @BeforeEach
    void setUp() {
        service = new IpInfoService(restClient);
    }

    @SuppressWarnings("unchecked")
    private void stubResponse(IpInfoService.IpInfoResponse response) {
        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) uriSpec);
        when(uriSpec.uri(anyString(), anyString())).thenReturn((RestClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(IpInfoService.IpInfoResponse.class)).thenReturn(response);
    }

    @Test
    void lookup_successfulResponsePopulatesIpInfo() {
        stubResponse(new IpInfoService.IpInfoResponse("host.example.com", "AS12345 Acme ISP", "Paris", "FR"));

        var result = service.lookup("1.2.3.4");

        assertEquals("1.2.3.4", result.ip());
        assertEquals("host.example.com", result.hostname());
        assertEquals("AS12345 Acme ISP", result.org());
        assertEquals("Paris", result.city());
        assertEquals("FR", result.country());
    }

    @Test
    void lookup_httpExceptionReturnsFallback() {
        when(restClient.get()).thenThrow(new RestClientException("timeout"));

        var result = service.lookup("5.6.7.8");

        assertEquals("5.6.7.8", result.ip());
        assertEquals("?", result.hostname());
        assertEquals("?", result.org());
        assertEquals("?", result.city());
        assertEquals("?", result.country());
    }

    @Test
    void lookup_secondCallReturnsCachedResultWithoutHttp() {
        stubResponse(new IpInfoService.IpInfoResponse("cached.host", "AS1 Org", "Lyon", "FR"));

        service.lookup("9.9.9.9");
        service.lookup("9.9.9.9");

        verify(restClient, times(1)).get();
    }
}
