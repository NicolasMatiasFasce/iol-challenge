package iolchallenge.model.api.error;

import java.io.Serial;

public class ApiHeaderMissingException extends ApiBadRequestException {

    @Serial
    private static final long serialVersionUID = 6419701186024532895L;

    public ApiHeaderMissingException(String message, String missingHeaderName) {
        super(message, ErrorResponse.missingRequiredHeader(missingHeaderName));
    }

}
