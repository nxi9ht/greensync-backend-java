package th.ac.rmutt.greensync.users.dto;

public class UpdateUserRequest {
  public String username;
  public String email;
  public String role;
  public Boolean is_active;
  public String password;
  public OrganizationRef organization;
  public Integer org_unit_id;
  public UserProfileRequest user_profile;

  public static class OrganizationRef {
    public Integer id;
  }
}
