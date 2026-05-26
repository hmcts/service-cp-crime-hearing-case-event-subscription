package uk.gov.hmcts.cp.servicebus.mapper;

import com.azure.messaging.servicebus.ServiceBusMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.subscription.services.JsonMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ServiceBusMapperTest {

    @Spy
    JsonMapper jsonMapper = new JsonMapper();

    @InjectMocks
    ServiceBusMapper serviceBusMapper;

    @Test
    void map_to_message_should_set_next_try_time() {
        OffsetDateTime nextTryTime = OffsetDateTime.of(2026, 3, 1, 12, 30, 15, 0, ZoneOffset.UTC);

        ServiceBusMessage message = serviceBusMapper.newMessage("wrapped-message", nextTryTime);

        assertThat(String.valueOf(message.getBody())).isEqualTo("wrapped-message");
        assertThat(message.getScheduledEnqueueTime()).isEqualTo(nextTryTime);
    }
}