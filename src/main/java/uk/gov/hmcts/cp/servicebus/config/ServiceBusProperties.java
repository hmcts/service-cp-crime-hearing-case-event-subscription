package uk.gov.hmcts.cp.servicebus.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Getter
public class ServiceBusProperties {
    public static final int ADMIN_CONNECTION_PORT = 5300;
    public static final String NOTIFICATIONS_INBOUND_QUEUE = "hrds.notifications.inbound";
    public static final String NOTIFICATIONS_OUTBOUND_QUEUE = "hrds.notifications.outbound";
    public static final String HTTPS = "https";

    private String adminConnectionString;
    private String connectionString;
    private int maxTries;

    public ServiceBusProperties(
            final @Value("${service-bus.admin-connection}") String adminConnectionString,
            final @Value("${service-bus.connection}") String connectionString,
            final @Value("${service-bus.max-tries}") int maxTries
    ) {
        log.info("ServiceBusProperties initialised with adminConnectionString starting:\"{}\"", adminConnectionString.substring(0, 20));
        log.info("ServiceBusProperties initialised with connectionString starting:\"{}\"", connectionString.substring(0, 20));
        log.info("ServiceBusProperties initialised with maxTries \"{}\"", maxTries);
        this.adminConnectionString = adminConnectionString;
        this.connectionString = connectionString;
        this.maxTries = maxTries;
    }

    public boolean isEmulator() {
        return !connectionString.contains(HTTPS);
    }

}
