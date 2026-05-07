package pl.zzpj.watermark_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Translates service-layer exceptions into concise JSON API responses.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * Converts illegal argument failures into HTTP 400 responses.
     *
     * @param exception thrown validation or processing exception
     * @return response containing a concise error message
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", exception.getMessage()));
    }

    /**
     * Preserves explicit HTTP status exceptions raised by the service layer.
     *
     * @param exception thrown status exception
     * @return response containing the original status code and reason
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return ResponseEntity.status(status)
                .body(Map.of("error", exception.getReason() == null ? status.getReasonPhrase() : exception.getReason()));
    }

    /**
     * Catches all remaining unhandled exceptions to prevent stack-trace leakage.
     *
     * @param exception unhandled exception
     * @return HTTP 500 response with a generic error message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
    }
}
