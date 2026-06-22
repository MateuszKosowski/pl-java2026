package pl.zzpj.auth_server.exception;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, String>> handleValidationExceptions(
      MethodArgumentNotValidException ex) {
    Map<String, String> errors = new HashMap<>();
    ex.getBindingResult()
        .getAllErrors()
        .forEach(
            (error) -> {
              String fieldName = ((FieldError) error).getField();
              String errorMessage = error.getDefaultMessage();
              errors.put(fieldName, errorMessage);
            });
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<String> handleJsonErrors(HttpMessageNotReadableException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body("Invalid JSON format or unknown properties in request.");
  }

  @ExceptionHandler(UnknownRegistrationPropertyException.class)
  public ResponseEntity<String> handleUnknownRegistrationProperty(
      UnknownRegistrationPropertyException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
  }

  @ExceptionHandler(DuplicateUserFieldException.class)
  public ResponseEntity<Map<String, String>> handleDuplicateUserField(
      DuplicateUserFieldException ex) {
    Map<String, String> errors = new HashMap<>();
    errors.put(ex.getField(), ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(errors);
  }

  @ExceptionHandler({BadCredentialsException.class, EmailNotFoundException.class})
  public ResponseEntity<String> handleAuthenticationErrors(Exception ex) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid email or password.");
  }
}
