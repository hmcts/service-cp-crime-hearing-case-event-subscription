package uk.gov.hmcts.cp.servicebus.config;

import com.azure.core.http.HttpClient;
import com.azure.core.http.netty.NettyAsyncHttpClientBuilder;
import com.azure.core.http.policy.HttpPipelinePolicy;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClient;
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClientBuilder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.net.MalformedURLException;
import java.net.URL;

@Slf4j
@Service
@Getter
public class ServiceBusConfigService {
    public static final int ADMIN_CONNECTION_PORT = 5300;
    public static final String PCR_INBOUND_QUEUE = "notifications.inbound";
    public static final String PCR_OUTBOUND_QUEUE = "notifications.outbound";

    private boolean enabled;
    private String adminConnectionString;
    private String connectionString;
    private int maxTries;

    public ServiceBusConfigService(
            final @Value("${service-bus.enabled}") boolean enabled,
            final @Value("${service-bus.admin-connection}") String adminConnectionString,
            final @Value("${service-bus.connection}") String connectionString,
            final @Value("${service-bus.max-tries}") int maxTries
    ) {
        log.info("ServiceBusConfigService initialised with enabled {}", enabled);
        log.info("ServiceBusConfigService initialised with adminConnectionString starting:\"{}\"", adminConnectionString.substring(0, 20));
        log.info("ServiceBusConfigService initialised with connectionString starting:\"{}\"", connectionString.substring(0, 20));
        log.info("ServiceBusConfigService initialised with maxTries \"{}\"", maxTries);
        this.enabled = enabled;
        this.adminConnectionString = adminConnectionString;
        this.connectionString = connectionString;
        this.maxTries = maxTries;
    }

    public ServiceBusAdministrationClient adminClient() {
        final HttpClient adminHttpClient = new NettyAsyncHttpClientBuilder()
                .port(ADMIN_CONNECTION_PORT)
                .build();
        final HttpPipelinePolicy forceHttpPolicy = (context, next) -> {
            try {
                final URL current = context.getHttpRequest().getUrl();
                final URL httpUrl = new URL("http", current.getHost(), ADMIN_CONNECTION_PORT, current.getFile());
                context.getHttpRequest().setUrl(httpUrl);
            } catch (MalformedURLException e) {
                return Mono.error(e);
            }
            return next.process();
        };

        return new ServiceBusAdministrationClientBuilder()
                .connectionString(adminConnectionString)
                .httpClient(adminHttpClient)
                .addPolicy(forceHttpPolicy)
                .buildClient();
    }

    public ServiceBusSenderClient senderClient(final String queueName) {
        return clientBuilder()
                .sender()
                .queueName(queueName)
                .buildClient();
    }

    public ServiceBusClientBuilder.ServiceBusProcessorClientBuilder processorClientBuilder(final String queueName) {
        return clientBuilder()
                .processor()
                .queueName(queueName);
    }

    private ServiceBusClientBuilder clientBuilder() {
        return new ServiceBusClientBuilder().connectionString(connectionString);
    }
}
