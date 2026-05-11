package uk.gov.hmcts.cp.subscription.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * This config disables SSL certificate checking in TEST ONLY
 * It is needed because
 * 1) We are forced to use callback url with a pattern of "pattern: '^https?://.*$'" "by the NotificationEndpoint OpenApi Spec
 * 2) When we use a url of https with WireMock, it enforces https TLS with certificates
 *
 * Our obvious options here are
 * a) Use a pattern of "pattern: '^https?://.*$'" which makes the "s" optional thus allow http in Test
 * b) Add the wiremock certificate to our Test trust store
 * c) Add this config to ignore SSL certs - Which we have gone with here
 */
@Configuration
public class IgnoreSSLCertificatesForWiremockTest {

    private static final X509TrustManager TRUST_ALL = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            if (chain == null || chain.length == 0) {
                throw new CertificateException("No server certificates");
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };

    @PostConstruct
    public void trustAllSsl() throws Exception {
        var ssl = SSLContext.getInstance("TLS");
        ssl.init(null, new TrustManager[]{TRUST_ALL}, new SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(ssl.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
    }
}
