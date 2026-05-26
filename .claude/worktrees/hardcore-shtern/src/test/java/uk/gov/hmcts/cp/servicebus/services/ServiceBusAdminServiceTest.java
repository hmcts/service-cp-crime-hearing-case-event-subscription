package uk.gov.hmcts.cp.servicebus.services;

import com.azure.core.http.rest.PagedIterable;
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClient;
import com.azure.messaging.servicebus.administration.models.CreateQueueOptions;
import com.azure.messaging.servicebus.administration.models.QueueProperties;
import com.azure.messaging.servicebus.administration.models.SubscriptionProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.servicebus.config.ServiceBusConfigService;

import java.time.Duration;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceBusAdminServiceTest {

    @Mock
    ServiceBusConfigService configService;

    @InjectMocks
    ServiceBusAdminService adminService;

    @Mock
    ServiceBusAdministrationClient serviceBusAdministrationClient;
    @Mock
    PagedIterable<QueueProperties> queues;
    @Mock
    QueueProperties queueProperties;

    @Captor
    ArgumentCaptor<CreateQueueOptions> queueOptionCaptor;

    @Test
    void service_bus_started_should_return_true() {
        when(configService.adminClient()).thenReturn(serviceBusAdministrationClient);
        when(serviceBusAdministrationClient.listQueues()).thenReturn(queues);

        assertThat(adminService.isServiceBusReady()).isTrue();
    }

    @Test
    void service_bus_down_should_throw() {
        when(configService.adminClient()).thenThrow(new RuntimeException("Connection refused"));
        assertThat(adminService.isServiceBusReady()).isFalse();
    }

    @Test
    void create_should_create_topic_and_subscription() {
        when(configService.adminClient()).thenReturn(serviceBusAdministrationClient);
        when(serviceBusAdministrationClient.getQueueExists("queue1")).thenReturn(false);

        adminService.createQueue("queue1");

        verify(serviceBusAdministrationClient).createQueue(eq("queue1"), queueOptionCaptor.capture());
        assertThat(queueOptionCaptor.getValue().getDefaultMessageTimeToLive()).isEqualTo(Duration.ofHours(1));
        assertThat(queueOptionCaptor.getValue().getLockDuration()).isEqualTo(Duration.ofMinutes(1));
        assertThat(queueOptionCaptor.getValue().getMaxDeliveryCount()).isEqualTo(1);
    }

    @Test
    void create_should_skip_topic_and_subscription_if_exists() {
        when(configService.adminClient()).thenReturn(serviceBusAdministrationClient);
        when(serviceBusAdministrationClient.getQueueExists("queue1")).thenReturn(true);

        adminService.createQueue("queue1");

        verify(serviceBusAdministrationClient, never()).createQueue(eq("queue1"), queueOptionCaptor.capture());
    }
}