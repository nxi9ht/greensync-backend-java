package th.ac.rmutt.greensync.users;

import th.ac.rmutt.greensync.organizations.Organization;

/** Everything needed to create a user + its profile + its role in one transaction. */
public record NewUserData(
    String email,
    String rawPassword,
    Organization organization,
    String firstName,
    String lastName,
    String phone,
    String role,
    boolean passwordSetupRequired) {

  public static NewUserData of(String email, String rawPassword, Organization organization, String role) {
    return new NewUserData(email, rawPassword, organization, null, null, null, role, false);
  }
}
