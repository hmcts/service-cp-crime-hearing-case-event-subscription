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
    private static final String CONNECTION_TIMEOUT_PARAMS = "sslEnabled=true&verifyHost=false&call_timeout=5000&connection_ttl=5000&initial_connect_attempts=1&reconnect_attempts=0";

    @Value("${cp.audit.hosts[0]}")
    private String primaryHost;

    @Value("${cp.audit.hosts[1]}")
    private String secondaryHost;

    @Scheduled(cron = "0 0 * * * *")
    public void checkConnectivity() {
        final boolean allOk = List.of(primaryHost, secondaryHost).stream().allMatch(this::checkHost);
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
            log.error("artemis connectivity check host:{} port:{} FAILED", host, PORT, e);
            return false;
        }
    }
}
