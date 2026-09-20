package th.ac.rmutt.greensync.users.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class CreateUserRequest {
  @Email public String email;
  @NotBlank public String password;
  public String role;
  public UserProfileRequest user_profile;
}
