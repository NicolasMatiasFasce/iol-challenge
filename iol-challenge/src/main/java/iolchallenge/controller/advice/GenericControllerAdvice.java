package iolchallenge.controller.advice;

import iolchallenge.model.api.error.ApiException;
import iolchallenge.model.api.error.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GenericControllerAdvice {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenericControllerAdvice.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> exception(ApiException apiException) {
        LOGGER.debug("Api error: {}, error response: {} (status code {})", apiException.getMessage(),
                apiException.getErrorResponse(), apiException.getHttpStatus());
        return new ResponseEntity<>(apiException.getErrorResponse(), apiException.getHttpStatus());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> exception(Exception exception) {
        final var response = new ResponseEntity<>(ErrorResponse.unknown(), HttpStatus.INTERNAL_SERVER_ERROR);
        LOGGER.error("Unexpected error: {}, error response: {} (status code {})", exception.getMessage(),
                response.getBody(), response.getStatusCode(), exception);
        return response;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> exception(MissingServletRequestParameterException requestParameterException) {
        final var response = new ResponseEntity<>(ErrorResponse.missingRequiredParameter(requestParameterException.getParameterName(), requestParameterException.getParameterType()), HttpStatus.BAD_REQUEST);
        LOGGER.warn("Bad request error: {}, error response: {} (status code {})", requestParameterException.getMessage(),
                response.getBody(), response.getStatusCode(), requestParameterException);
        return response;
    }


}
