package uk.gov.hmcts.cp.subscription.controllers;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.core.ConditionTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.validation.FieldError;
import uk.gov.hmcts.cp.openapi.model.ErrorResponse;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String CALLBACK_NOT_READY = "Callback is not ready";
    private static final String MATERIAL_NOT_READY = "Material metadata not ready";
    private static final String ERROR_NOT_FOUND = "not_found";
    private static final String ERROR_INVALID_REQUEST = "invalid_request";
    private static final String ERROR_UNSUPPORTED_MEDIA_TYPE = "unsupported_media_type";
    private static final String ERROR_INTERNAL = "internal_error";
    private static final String ERROR_TIMEOUT = "gateway_timeout";
    public static final String NOTIFICATION_ENDPOINT_CALLBACK_URL = "notificationEndpoint.callbackUrl";
    public static final String EVENT_TYPES = "eventTypes";
    public static final String NOTIFICATION_ENDPOINT = "notificationEndpoint";
    public static final String SIZE = "Size";
    public static final String NOT_NULL = "NotNull";

    @ExceptionHandler({EntityNotFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFoundException(final Exception exception) {
        log.error("NotFoundException {}", exception.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(errorResponse(ERROR_NOT_FOUND, exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(final MethodArgumentNotValidException exception) {
        final String message = exception.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toMessage)
                .collect(Collectors.joining("; "));
        log.error("Validation failed: {}", message);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse(ERROR_INVALID_REQUEST, message));
    }

    private static String toMessage(final FieldError fieldError) {
        final String field = fieldError.getField();
        final String code = fieldError.getCode();
        if (EVENT_TYPES.equals(field) && SIZE.equals(code)) {
            return "eventTypes must contain at least one value";
        }
        if (NOTIFICATION_ENDPOINT.equals(field) && NOT_NULL.equals(code)) {
            return "notificationEndpoint is required";
        }
        if (NOTIFICATION_ENDPOINT_CALLBACK_URL.equals(field)) {
            return "notificationEndpoint.callbackUrl must be a valid HTTPS URL";
        }
        return field + " " + fieldError.getDefaultMessage();
    }

    @ExceptionHandler({
            ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> handleBadRequestException(final Exception exception) {
        log.error("Validation failed: {}", exception.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse(ERROR_INVALID_REQUEST, exception.getMessage()));
    }

    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<ErrorResponse> handleClientException(final HttpClientErrorException exception) {
        log.error("HttpClientErrorException {}", exception.getMessage());
        return ResponseEntity
                .status(exception.getStatusCode())
                .body(errorResponse(ERROR_INVALID_REQUEST, exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknownException(final Exception exception) {
        log.error("Exception {}", exception.getMessage());
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse(ERROR_INTERNAL, exception.getMessage()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleHttpMediaTypeNotSupportedException(
            final HttpMediaTypeNotSupportedException exception) {
        log.error("Unsupported media type - HttpMediaTypeNotSupportedException: {}", exception.getMessage(), exception);
        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(errorResponse(ERROR_UNSUPPORTED_MEDIA_TYPE, exception.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(final ResponseStatusException exception) {
        log.error("ResponseStatusException: {}", exception.getMessage());
        final String message = exception.getReason() != null ? exception.getReason() : exception.getMessage();
        return ResponseEntity
                .status(exception.getStatusCode())
                .body(errorResponse(ERROR_INVALID_REQUEST, message));
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedOperation(final UnsupportedOperationException exception) {
        log.error("Unsupported operation: {}", exception.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_IMPLEMENTED)
                .body(errorResponse(ERROR_INVALID_REQUEST, "Unsupported"));
    }

    @ExceptionHandler(ConditionTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleConditionTimeout(final ConditionTimeoutException ex) {
        final boolean isCallbackTimeout = CALLBACK_NOT_READY.equals(ex.getMessage());
        if (isCallbackTimeout) {
            log.error("Callback delivery timed out: {}", ex.getMessage());
        } else {
            log.error("Material metadata timed out: {}", ex.getMessage());
        }
        final String message = isCallbackTimeout ? CALLBACK_NOT_READY : MATERIAL_NOT_READY;
        return ResponseEntity
                .status(HttpStatus.GATEWAY_TIMEOUT)
                .body(errorResponse(ERROR_TIMEOUT, message));
    }

    private static ErrorResponse errorResponse(final String error, final String message) {
        final ErrorResponse response = new ErrorResponse();
        response.setError(error);
        response.setMessage(message);
        return response;
    }
}