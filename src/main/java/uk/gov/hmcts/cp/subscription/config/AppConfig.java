package uk.gov.hmcts.cp.subscription.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.cp.subscription.services.ClockService;
import uk.gov.hmcts.cp.subscription.services.JsonMapper;
import uk.gov.hmcts.cp.subscription.util.JwtTokenParser;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.List;


@Configuration
@Slf4j
public class AppConfig {

    @Bean
    public JwtTokenParser jwtTokenParser(final JsonMapper jsonMapper) {
        return new JwtTokenParser(jsonMapper);
    }

    @Bean
    public RestTemplate restTemplate(final OutboundTracingInterceptor outboundCorrelationIdInterceptor,
                                     @Value("${environment.name}") final EnvironmentName environmentName)
            throws NoSuchAlgorithmException, KeyManagementException {
        log.info("Initialised restTemplate with EnvironmentName:{}", environmentName);
        final RestTemplate restTemplate = isLocalEnvironment(environmentName)
                ? new RestTemplate(trustAllRequestFactory(environmentName))
                : new RestTemplate();
        restTemplate.setInterceptors(List.of(outboundCorrelationIdInterceptor));
        return restTemplate;
    }

    @Bean
    public ClockService clockService() {
        return new ClockService(Clock.systemDefaultZone());
    }

    private boolean isLocalEnvironment(final EnvironmentName environmentName) {
        return EnvironmentName.LOCAL.equals(environmentName) || EnvironmentName.DEV.equals(environmentName);
    }

    // TODO: remove once callbackUrl pattern in openapi-spec.yml is relaxed to allow http for local envs
    // Trusts all SSL certs — LOCAL/DEV only, so callbacks to WireMock over HTTPS work without a signed cert
    @SuppressWarnings("PMD.UseShortArrayInitializer")
    private SimpleClientHttpRequestFactory trustAllRequestFactory(final EnvironmentName environmentName) throws NoSuchAlgorithmException, KeyManagementException {
        log.warn("Setting trustAll in environment {}", environmentName);
        final TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    @Override
                    public void checkClientTrusted(final X509Certificate[] c, final String a) {
                    }

                    @Override
                    public void checkServerTrusted(final X509Certificate[] c, final String a) {
                    }
                }
        };
        final SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAll, new SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        return new SimpleClientHttpRequestFactory();
    }
}
