package th.ac.rmutt.greensync.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates exceptions into the same {@code { "statusCode": n, "message": "..." } } JSON shape
 * the NestJS backend returns, since the Angular error interceptor reads {@code error.error.message}
 * directly and shows it as a toast.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<Map<String, Object>> handleApiException(ApiException ex) {
    return body(ex.getStatus(), ex.getMessage());
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
    return body(HttpStatus.FORBIDDEN, "Forbidden resource");
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(e -> e.getDefaultMessage())
            .orElse("ข้อมูลที่ส่งมาไม่ถูกต้อง");
    return body(HttpStatus.BAD_REQUEST, message);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
    log.error("Unhandled exception", ex);
    return body(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
  }

  private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("statusCode", status.value());
    payload.put("message", message);
    return ResponseEntity.status(status).body(payload);
  }
}
