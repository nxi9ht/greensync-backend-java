package th.ac.rmutt.greensync.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ResetPasswordRequest {
  @NotBlank public String token;

  @NotBlank
  @Size(min = 6, message = "รหัสผ่านต้องมีอย่างน้อย 6 ตัวอักษร")
  @Pattern(
      regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d@$!%*?&]+$",
      message = "รหัสผ่านต้องประกอบด้วยตัวอักษรและตัวเลขอย่างน้อย 1 ตัว")
  public String password;
}
