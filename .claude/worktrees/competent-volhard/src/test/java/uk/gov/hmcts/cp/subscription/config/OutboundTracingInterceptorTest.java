package uk.gov.hmcts.cp.subscription.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import uk.gov.hmcts.cp.filters.TracingFilter;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.cp.filters.TracingFilter.CORRELATION_ID_KEY;

@ExtendWith(MockitoExtension.class)
class OutboundTracingInterceptorTest {

    @InjectMocks
    OutboundTracingInterceptor interceptor;

    @Mock
    HttpRequest request;

    @Mock
    ClientHttpRequestExecution execution;

    @Mock
    ClientHttpResponse clientHttpResponse;

    @BeforeEach
    void setUp() {
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void intercept_adds_X_Correlation_Id_from_MDC() throws Exception {
        final String correlationId = UUID.randomUUID().toString();
        MDC.put(CORRELATION_ID_KEY, correlationId);
        final HttpHeaders headers = new HttpHeaders();
        when(request.getHeaders()).thenReturn(headers);
        when(execution.execute(request, new byte[0])).thenReturn(clientHttpResponse);

        interceptor.intercept(request, new byte[0], execution);

        assertThat(headers.getFirst(TracingFilter.CORRELATION_ID_KEY))
                .isEqualTo(correlationId);
    }

    @Test
    void intercept_does_not_add_header_when_MDC_correlationId_absent() throws Exception {
        final HttpHeaders headers = new HttpHeaders();
        when(request.getHeaders()).thenReturn(headers);
        when(execution.execute(request, new byte[0])).thenReturn(clientHttpResponse);

        interceptor.intercept(request, new byte[0], execution);

        assertThat(request.getHeaders().getFirst(TracingFilter.CORRELATION_ID_KEY)).isNull();
    }
}