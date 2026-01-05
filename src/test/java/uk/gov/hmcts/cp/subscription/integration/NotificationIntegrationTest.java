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
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = TestContainersInitialise.class)
@AutoConfigureMockMvc
@EnableWireMock({@ConfigureWireMock(name = "material-client", baseUrlProperties = "material-client.url")})
class NotificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnCreated_whenPostingFullJson() throws Exception {
        UUID materialId = UUID.randomUUID();

        // Load request JSON from resources/request/pcr-event.json
        ClassPathResource resource = new ClassPathResource("requests/pcr-request.json");
        String requestJson = Files.readString(resource.getFile().toPath());

        // Perform POST request
        mockMvc.perform(post("/notifications/pcr/7c198796-08bb-4803-b456-fa0c29ca6021", materialId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated());
    }
}
