package th.ac.rmutt.greensync.auth;

import jakarta.validation.Valid;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import th.ac.rmutt.greensync.auth.dto.ForgotPasswordRequest;
import th.ac.rmutt.greensync.auth.dto.LoginRequest;
import th.ac.rmutt.greensync.auth.dto.RegisterAssessorRequest;
import th.ac.rmutt.greensync.auth.dto.RegisterRequest;
import th.ac.rmutt.greensync.auth.dto.ResetPasswordRequest;

@RestController
@RequestMapping("/auth")
public class AuthController {

  private static final Logger log = LoggerFactory.getLogger(AuthController.class);

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/register")
  public Map<String, Object> register(@Valid @RequestBody RegisterRequest request) {
    log.info("Register request for: {}", request.userData.email);
    return authService.register(request);
  }

  @PostMapping("/register/assessor")
  public Map<String, Object> registerAssessor(@Valid @RequestBody RegisterAssessorRequest request) {
    log.info("Register Assessor request for: {}", request.userData.email);
    return authService.registerAssessor(request);
  }

  @PostMapping("/login")
  public Map<String, Object> login(@Valid @RequestBody LoginRequest request) {
    log.info("Login request for: {}", request.email);
    return authService.login(request);
  }

  @PostMapping("/forgot-password")
  public Map<String, String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
    log.info("Forgot password request for: {}", request.email);
    return authService.forgotPassword(request.email);
  }

  @PostMapping("/reset-password")
  public Map<String, String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
    log.info("Reset password request received");
    return authService.resetPassword(request.token, request.password);
  }

  @GetMapping("/verify-email")
  public Map<String, Object> verifyEmail(@RequestParam String token) {
    log.info("Verify email request received");
    return authService.verifyEmail(token);
  }
}
