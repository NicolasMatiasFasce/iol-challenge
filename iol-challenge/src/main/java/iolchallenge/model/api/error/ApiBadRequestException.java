package iolchallenge.model.api.error;

import org.springframework.http.HttpStatus;

import java.io.Serial;

public class ApiBadRequestException extends ApiException {

    @Serial
    private static final long serialVersionUID = 4586005144679023845L;

    public ApiBadRequestException(String message, ErrorResponse errorResponse) {
        super(message, HttpStatus.BAD_REQUEST, errorResponse);
    }

}
