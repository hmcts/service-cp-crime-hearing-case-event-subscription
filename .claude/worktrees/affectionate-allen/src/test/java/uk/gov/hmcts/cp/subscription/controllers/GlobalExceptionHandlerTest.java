package uk.gov.hmcts.cp.subscription.controllers;

import jakarta.persistence.EntityNotFoundException;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import uk.gov.hmcts.cp.openapi.model.ErrorResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @InjectMocks
    GlobalExceptionHandler globalExceptionHandler;

    @Test
    void not_found_exceptions_should_handle_ok() {
        NoHandlerFoundException e = new NoHandlerFoundException("GET", "Url", null);
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleNotFoundException(e);
        assertErrorFields(response, NOT_FOUND, "not_found", "No endpoint GET Url.");

        EntityNotFoundException e2 = new EntityNotFoundException("Not found message", null);
        ResponseEntity<ErrorResponse> response2 = globalExceptionHandler.handleNotFoundException(e2);
        assertErrorFields(response2, NOT_FOUND, "not_found", "Not found message");
    }

    @Test
    void error_response_should_handle_ok() {
        ResponseStatusException e = new ResponseStatusException(INTERNAL_SERVER_ERROR, "Error");
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleResponseStatusException(e);
        assertErrorFields(response, INTERNAL_SERVER_ERROR, "invalid_request", "Error");
    }

    @Test
    void client_exception_should_handle_ok() {
        HttpClientErrorException e = new HttpClientErrorException(NOT_FOUND, "Error");
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleClientException(e);
        assertErrorFields(response, NOT_FOUND, "invalid_request", "404 Error");
    }

    @Test
    void generic_exception_should_handle_ok() {
        Exception e = new Exception("message");
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleUnknownException(e);
        assertErrorFields(response, INTERNAL_SERVER_ERROR, "internal_error", "message");
    }

    @Test
    void unsupported_media_type_should_handle_ok() {
        HttpMediaTypeNotSupportedException e = new HttpMediaTypeNotSupportedException("application/xml");
        ResponseEntity<ErrorResponse> response =
                globalExceptionHandler.handleHttpMediaTypeNotSupportedException(e);
        assertErrorFields(response, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported_media_type", e.getMessage());
    }

    @Test
    void unsupported_operation_should_handle_ok() {
        UnsupportedOperationException e = new UnsupportedOperationException("not implemented");
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleUnsupportedOperation(e);
        assertErrorFields(response, HttpStatus.NOT_IMPLEMENTED, "invalid_request", "Unsupported");
    }

    @Test
    void condition_timeout_material_should_handle_ok() {
        ConditionTimeoutException e = new ConditionTimeoutException("timed out");
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleConditionTimeout(e);
        assertErrorFields(response, HttpStatus.GATEWAY_TIMEOUT, "gateway_timeout", "Material metadata not ready");
    }

    @Test
    void condition_timeout_callback_should_handle_ok() {
        ConditionTimeoutException e = new ConditionTimeoutException("Callback is not ready");
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleConditionTimeout(e);
        assertErrorFields(response, HttpStatus.GATEWAY_TIMEOUT, "gateway_timeout", "Callback is not ready");
    }

    @Test
    void empty_event_types_should_return_error_message() {
        final BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new Object(), "clientSubscriptionRequest");
        bindingResult.addError(new FieldError(
                "clientSubscriptionRequest", "eventTypes", null, false,
                new String[]{"Size.clientSubscriptionRequest.eventTypes", "Size.eventTypes", "Size"},
                new Object[]{2, 1}, "size must be between 1 and 2"));

        final MethodArgumentNotValidException e = new MethodArgumentNotValidException(null, bindingResult);
        final ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleMethodArgumentNotValid(e);

        assertErrorFields(response, BAD_REQUEST, "invalid_request", "eventTypes must contain at least one value");
    }

    @Test
    void missing_notification_endpoint_should_return_error_message() {
        final BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new Object(), "clientSubscriptionRequest");
        bindingResult.addError(new FieldError(
                "clientSubscriptionRequest", "notificationEndpoint", null, false,
                new String[]{"NotNull.clientSubscriptionRequest.notificationEndpoint", "NotNull"},
                null, "must not be null"));

        final MethodArgumentNotValidException e = new MethodArgumentNotValidException(null, bindingResult);
        final ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleMethodArgumentNotValid(e);

        assertErrorFields(response, BAD_REQUEST, "invalid_request", "notificationEndpoint is required");
    }

    @Test
    void invalid_callback_url_should_return_error_message() {
        final BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new Object(), "clientSubscriptionRequest");
        bindingResult.addError(new FieldError(
                "clientSubscriptionRequest", "notificationEndpoint.callbackUrl", "http://bad",
                false, new String[]{"Pattern"}, null, "must match \"^https://.*$\""));

        final MethodArgumentNotValidException e = new MethodArgumentNotValidException(null, bindingResult);
        final ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleMethodArgumentNotValid(e);

        assertErrorFields(response, BAD_REQUEST, "invalid_request",
                "notificationEndpoint.callbackUrl must be a valid HTTPS URL");
    }

    @Test
    void conflict_response_status_exception_should_return_409_with_message() {
        ResponseStatusException e = new ResponseStatusException(CONFLICT, "subscription already exist with some-id");
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleResponseStatusException(e);
        assertErrorFields(response, CONFLICT, "invalid_request", "subscription already exist with some-id");
    }

    private void assertErrorFields(final ResponseEntity<ErrorResponse> response,
                                   final HttpStatusCode expectedStatus,
                                   final String expectedError,
                                   final String expectedMessage) {
        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo(expectedError);
        assertThat(response.getBody().getMessage()).isEqualTo(expectedMessage);
    }
}
