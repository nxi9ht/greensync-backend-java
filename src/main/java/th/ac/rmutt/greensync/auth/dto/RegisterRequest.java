package th.ac.rmutt.greensync.auth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public class RegisterRequest {
  @NotNull @Valid public UserDataRequest userData;
  @NotNull @Valid public OrgDataRequest orgData;
}
