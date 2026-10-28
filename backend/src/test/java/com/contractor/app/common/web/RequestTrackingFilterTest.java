package com.contractor.app.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestTrackingFilterTest {

    private final RequestTrackingFilter filter = new RequestTrackingFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void preservesSafeClientRequestIdAndReturnsItInResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        request.addHeader(RequestTrackingFilter.HEADER_NAME, "client-request_123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            assertThat(MDC.get(RequestTrackingFilter.MDC_KEY)).isEqualTo("client-request_123");
        });

        assertThat(response.getHeader(RequestTrackingFilter.HEADER_NAME)).isEqualTo("client-request_123");
        assertThat(MDC.get(RequestTrackingFilter.MDC_KEY)).isNull();
    }

    @Test
    void replacesInvalidClientRequestIdWithGeneratedUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        request.addHeader(RequestTrackingFilter.HEADER_NAME, "bad id\r\nInjected-Header: yes");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            String requestId = MDC.get(RequestTrackingFilter.MDC_KEY);
            assertThat(requestId).isNotNull();
            assertThat(requestId).matches("[0-9a-fA-F-]{36}");
        });

        String responseId = response.getHeader(RequestTrackingFilter.HEADER_NAME);
        assertThat(responseId).matches("[0-9a-fA-F-]{36}");
        assertThat(responseId).doesNotContain("\r", "\n");
        assertThat(MDC.get(RequestTrackingFilter.MDC_KEY)).isNull();
    }

    @Test
    void generatesRequestIdWhenClientDoesNotSupplyOne() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                assertThat(MDC.get(RequestTrackingFilter.MDC_KEY)).isNotBlank());

        assertThat(response.getHeader(RequestTrackingFilter.HEADER_NAME)).isNotBlank();
        assertThat(MDC.get(RequestTrackingFilter.MDC_KEY)).isNull();
    }
}
