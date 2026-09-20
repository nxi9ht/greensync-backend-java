package th.ac.rmutt.greensync.auth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class RegisterAssessorRequest {
  @NotNull @Valid public UserDataRequest userData;
  @NotNull @Valid public AssessorProfileDataRequest profileData;

  public static class AssessorProfileDataRequest {
    @NotBlank public String firstName;
    @NotBlank public String lastName;
    public String phone;
    @NotBlank public String license_number;
    @NotBlank public String years_experience;
    @NotBlank public String education_background;
    public String qualification_file_url;
    public String bank_name;
    public String bank_account_no;
    public String bank_account_name;
  }
}
