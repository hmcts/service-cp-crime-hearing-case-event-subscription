package uk.gov.hmcts.cp.artemis;

import jakarta.jms.Connection;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class ArtemisConnectivityChecker {

    private static final int PORT = 61_616;
    private static final String CONNECTION_TIMEOUT_PARAMS = "call_timeout=5000&connection_ttl=5000&initial_connect_attempts=1&reconnect_attempts=0";

    @Value("${artemis.host.primary}")
    private String primaryHost;

    @Value("${artemis.host.secondary}")
    private String secondaryHost;

    @Scheduled(cron = "0 0 * * * *")
    public void checkConnectivity() {
        boolean allOk = true;
        for (final String host : List.of(primaryHost, secondaryHost)) {
            allOk &= checkHost(host);
        }
        if (allOk) {
            log.info("artemis connectivity check successful primaryHost:{} secondaryHost:{}", primaryHost, secondaryHost);
        }
    }

    private boolean checkHost(final String host) {
        final String url = "tcp://" + host + ":" + PORT + "?" + CONNECTION_TIMEOUT_PARAMS;
        try (ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(url);
             Connection connection = factory.createConnection("", "")) {
            connection.start();
            log.info("artemis connectivity check host:{} port:{} OK", host, PORT);
            return true;
        } catch (Exception e) {
            log.error("artemis connectivity check host:{} port:{} FAILED ERROR:{}", host, PORT, e.getMessage());
            return false;
        }
    }
}
