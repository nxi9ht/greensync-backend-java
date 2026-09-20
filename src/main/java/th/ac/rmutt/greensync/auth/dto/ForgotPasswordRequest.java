package th.ac.rmutt.greensync.auth.dto;

import jakarta.validation.constraints.Email;

public class ForgotPasswordRequest {
  @Email(message = "รูปแบบอีเมลไม่ถูกต้อง")
  public String email;
}
