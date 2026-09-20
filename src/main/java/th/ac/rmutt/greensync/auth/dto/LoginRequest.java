package th.ac.rmutt.greensync.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class LoginRequest {
  @Email(message = "รูปแบบอีเมลไม่ถูกต้อง")
  public String email;

  @NotBlank(message = "กรุณากรอกรหัสผ่าน")
  public String password;
}
