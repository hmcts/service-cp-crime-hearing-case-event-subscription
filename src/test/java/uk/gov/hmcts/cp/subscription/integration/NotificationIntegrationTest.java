package uk.gov.hmcts.cp.subscription.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import uk.gov.hmcts.cp.subscription.config.TestContainersInitialise;

import java.nio.file.Files;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = TestContainersInitialise.class)
@AutoConfigureMockMvc
@EnableWireMock({@ConfigureWireMock(name = "material-client", baseUrlProperties = "material-client.url")})
class NotificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void should_return_created_when_posting_valid_payload() throws Exception {
        ClassPathResource resource = new ClassPathResource("requests/pcr-request.json");
        String requestJson = Files.readString(resource.getFile().toPath());

        mockMvc.perform(post("/notification/pcr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andDo(print())
                .andExpect(status().isAccepted());
    }
}
