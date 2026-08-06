package uk.gov.hmcts.cp.subscription.integration.controllers;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.hmcts.cp.filters.UUIDService;
import uk.gov.hmcts.cp.subscription.integration.IntegrationTestBase;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.hmcts.cp.filters.TracingFilter.CORRELATION_ID_KEY;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "jwt.filter.enabled=false",
                "management.tracing.enabled=true",
                "spring.application.name=cp-crime-hearing-results-document-subscription"
        }
)
@AutoConfigureMockMvc
class TracingIntegrationTest extends IntegrationTestBase {

    private static final String TEST_CORRELATION_ID = "12345678-1234-1234-1234-123456789012";
    private static final String GENERATED_CORRELATION_ID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

    @MockitoBean
    private UUIDService uuidService;

    @Value("${spring.application.name}")
    private String springApplicationName;

    @Resource
    private MockMvc mockMvc;

    @Test
    void root_should_return_ok() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());
    }

    @Test
    void no_incoming_correlation_id_should_add_one() throws Exception {
        when(uuidService.randomString())
                .thenReturn(GENERATED_CORRELATION_ID)
                .thenThrow(new RuntimeException("Only get single correlationId"));

        MvcResult result = mockMvc.perform(get("/client-subscriptions/{id}", UUID.randomUUID())
                        .header("X-Client-Id", TEST_CLIENT_ID.toString()))
                .andReturn();

        assertThat(result.getResponse().getHeader(CORRELATION_ID_KEY)).isEqualTo(GENERATED_CORRELATION_ID);
    }

    @Test
    void incoming_correlation_id_should_be_used() throws Exception {
        MvcResult result = mockMvc.perform(get("/client-subscriptions/{id}", UUID.randomUUID())
                        .header(CORRELATION_ID_KEY, TEST_CORRELATION_ID)
                        .header("X-Client-Id", "11111111-2222-3333-4444-555555555555"))
                .andReturn();

        String responseCorrelationId = result.getResponse().getHeader(CORRELATION_ID_KEY);
        assertThat(responseCorrelationId).isEqualTo(TEST_CORRELATION_ID);
        assertThat(springApplicationName).isEqualTo("cp-crime-hearing-results-document-subscription");
    }
}
