package iolchallenge.model.api.error;

import java.io.Serializable;

public record ErrorResponse(ErrorCode code, String message) implements Serializable {

    public static ErrorResponse missingRequiredHeader(String headerName) {
        final var errorMessage = "Missing required header. Header '%s' is required.".formatted(headerName);
        return new ErrorResponse(ErrorCode.MISSING_REQUIRED_HEADER, errorMessage);
    }

    public static ErrorResponse unknown() {
        return new ErrorResponse(ErrorCode.UNKNOWN, "An unexpected error occurred.");
    }

    public static ErrorResponse missingRequiredParameter(String fieldName, String fieldType) {
        var message = "Missing required field '%s' of type '%s'".formatted(fieldName, fieldType);
        return new ErrorResponse(ErrorCode.MISSING_REQUIRED_PARAMETER, message);
    }
}