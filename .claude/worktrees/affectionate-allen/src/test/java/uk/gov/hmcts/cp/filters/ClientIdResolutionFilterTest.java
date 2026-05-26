package uk.gov.hmcts.cp.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.client.HttpClientErrorException;
import uk.gov.hmcts.cp.subscription.config.SubscriptionClientConfig;
import uk.gov.hmcts.cp.subscription.util.JwtTokenParser;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientIdResolutionFilterTest {

    private static final String CLIENT_SUBSCRIPTIONS_PATH = "/client-subscriptions";
    private static final String NOTIFICATIONS_PREFIX = "/notifications";

    @Mock
    private HttpServletRequest httpRequest;
    @Mock
    private FilterChain filterChain;
    @Mock
    private JwtTokenParser jwtTokenParser;

    private MockHttpServletResponse httpResponse;
    private ClientIdResolutionFilter filter;

    @BeforeEach
    void setUp() {
        httpResponse = new MockHttpServletResponse();
        SubscriptionClientConfig configOauthEnabled = new SubscriptionClientConfig(true);
        filter = new ClientIdResolutionFilter(jwtTokenParser, configOauthEnabled);
    }

    @Test
    void path_outside_client_subscriptions_should_not_be_filtered() throws Exception {
        when(httpRequest.getRequestURI()).thenReturn("/actuator/health");
        filter.doFilter(httpRequest, httpResponse, filterChain);
        verify(filterChain).doFilter(httpRequest, httpResponse);
        assertThat(MDC.get(ClientIdResolutionFilter.MDC_CLIENT_ID)).isNull();
    }

    @Test
    void path_client_subscriptions_with_id_should_be_filtered() throws Exception {
        UUID testClientUuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        when(httpRequest.getRequestURI()).thenReturn(CLIENT_SUBSCRIPTIONS_PATH + "/123");
        when(jwtTokenParser.extractClientIdFromToken(httpRequest)).thenReturn(testClientUuid);

        filter.doFilter(httpRequest, httpResponse, filterChain);

        verify(filterChain).doFilter(httpRequest, httpResponse);
        assertThat(MDC.get(ClientIdResolutionFilter.MDC_CLIENT_ID)).isNull();
    }

    @Test
    void path_notifications_should_not_be_filtered() throws Exception {
        when(httpRequest.getRequestURI()).thenReturn(NOTIFICATIONS_PREFIX );
        filter.doFilter(httpRequest, httpResponse, filterChain);
        verify(filterChain).doFilter(httpRequest, httpResponse);
        assertThat(MDC.get(ClientIdResolutionFilter.MDC_CLIENT_ID)).isNull();
    }

    @Test
    void valid_bearer_with_azp_should_put_client_id_in_mdc_and_continue() throws Exception {
        UUID testClientUuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        when(httpRequest.getRequestURI()).thenReturn(CLIENT_SUBSCRIPTIONS_PATH);
        when(jwtTokenParser.extractClientIdFromToken(httpRequest)).thenReturn(testClientUuid);

        filter.doFilter(httpRequest, httpResponse, filterChain);

        verify(filterChain).doFilter(httpRequest, httpResponse);
        assertThat(MDC.get(ClientIdResolutionFilter.MDC_CLIENT_ID)).isNull();
    }

    @Test
    void no_client_id_in_token_should_return_401() throws Exception {
        when(httpRequest.getRequestURI()).thenReturn(CLIENT_SUBSCRIPTIONS_PATH);
        when(jwtTokenParser.extractClientIdFromToken(httpRequest))
            .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "Missing or invalid authorisation token"));

        filter.doFilter(httpRequest, httpResponse, filterChain);

        assertThat(httpResponse.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(httpRequest, httpResponse);
    }

    @Test
    void oauth_disabled_with_client_id_header_should_put_header_value_in_mdc() throws Exception {
        UUID clientId = UUID.fromString("aaaaaaaa-bbbb-4ccc-dddd-eeeeeeeeeeee");
        when(httpRequest.getRequestURI()).thenReturn(CLIENT_SUBSCRIPTIONS_PATH);
        when(httpRequest.getHeader("X-Client-Id")).thenReturn(clientId.toString());
        SubscriptionClientConfig configOauthDisabled = new SubscriptionClientConfig(false);
        ClientIdResolutionFilter filterOauthDisabled = new ClientIdResolutionFilter(jwtTokenParser, configOauthDisabled);

        filterOauthDisabled.doFilter(httpRequest, httpResponse, filterChain);

        verify(filterChain).doFilter(httpRequest, httpResponse);
        assertThat(MDC.get(ClientIdResolutionFilter.MDC_CLIENT_ID)).isNull();
    }

    @Test
    void oauth_disabled_with_header_should_resolve_different_clients_per_request() throws Exception {
        UUID client1 = UUID.fromString("aaaaaaaa-1111-4444-8888-111111111111");
        when(httpRequest.getRequestURI()).thenReturn(CLIENT_SUBSCRIPTIONS_PATH);
        when(httpRequest.getHeader("X-Client-Id")).thenReturn(client1.toString());
        SubscriptionClientConfig configOauthDisabled = new SubscriptionClientConfig(false);
        ClientIdResolutionFilter filterOauthDisabled = new ClientIdResolutionFilter(jwtTokenParser, configOauthDisabled);

        filterOauthDisabled.doFilter(httpRequest, httpResponse, filterChain);
        verify(filterChain).doFilter(httpRequest, httpResponse);
        assertThat(MDC.get(ClientIdResolutionFilter.MDC_CLIENT_ID)).isNull();

        UUID client2 = UUID.fromString("bbbbbbbb-2222-4444-8888-222222222222");
        when(httpRequest.getHeader("X-Client-Id")).thenReturn(client2.toString());
        filterOauthDisabled.doFilter(httpRequest, httpResponse, filterChain);
        verify(filterChain, times(2)).doFilter(httpRequest, httpResponse);
        assertThat(MDC.get(ClientIdResolutionFilter.MDC_CLIENT_ID)).isNull();
    }

    @Test
    void oauth_disabled_with_null_header_should_return_401() throws Exception {
        when(httpRequest.getRequestURI()).thenReturn(CLIENT_SUBSCRIPTIONS_PATH);
        when(httpRequest.getHeader("X-Client-Id")).thenReturn(null);
        SubscriptionClientConfig configOauthDisabled = new SubscriptionClientConfig(false);
        ClientIdResolutionFilter filterOauthDisabled = new ClientIdResolutionFilter(jwtTokenParser, configOauthDisabled);

        filterOauthDisabled.doFilter(httpRequest, httpResponse, filterChain);

        assertThat(httpResponse.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(httpRequest, httpResponse);
    }

    @Test
    void oauth_disabled_with_invalid_uuid_header_should_return_401() throws Exception {
        when(httpRequest.getRequestURI()).thenReturn(CLIENT_SUBSCRIPTIONS_PATH);
        when(httpRequest.getHeader("X-Client-Id")).thenReturn("not-a-valid-uuid");
        SubscriptionClientConfig configOauthDisabled = new SubscriptionClientConfig(false);
        ClientIdResolutionFilter filterOauthDisabled = new ClientIdResolutionFilter(jwtTokenParser, configOauthDisabled);

        filterOauthDisabled.doFilter(httpRequest, httpResponse, filterChain);

        assertThat(httpResponse.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(httpRequest, httpResponse);
    }

    @Test
    void unexpected_exception_in_filter_should_also_return_401() throws Exception {
        when(httpRequest.getRequestURI()).thenReturn(CLIENT_SUBSCRIPTIONS_PATH);
        when(jwtTokenParser.extractClientIdFromToken(httpRequest))
            .thenThrow(new RuntimeException("unexpected failure"));

        filter.doFilter(httpRequest, httpResponse, filterChain);

        assertThat(httpResponse.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(httpRequest, httpResponse);
    }
}
