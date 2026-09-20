package th.ac.rmutt.greensync.users;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import th.ac.rmutt.greensync.organizations.Organization;

/**
 * Builds the same flattened JSON shape the NestJS UsersService returns (profile fields merged
 * onto the user, first role surfaced as a plain {@code role} string, an assessor's bank details
 * merged onto their {@code assessor_profile}). A plain map is used deliberately, mirroring the
 * object-spread style of the original service, so the wire format needs zero frontend changes.
 */
@Component
public class UserMapper {

  /** Full detail shape, as returned by {@code GET /users/:id} and {@code GET /users/profile/me}. */
  public Map<String, Object> toDetail(User user) {
    Map<String, Object> map = base(user);

    if (user.getAssessorProfile() != null) {
      AssessorProfile ap = user.getAssessorProfile();
      Map<String, Object> assessorProfile = new LinkedHashMap<>();
      assessorProfile.put("id", ap.getId());
      assessorProfile.put("license_number", ap.getLicenseNumber());
      assessorProfile.put("years_experience", ap.getYearsExperience());
      assessorProfile.put("education_background", ap.getEducationBackground());
      assessorProfile.put("qualification_file_url", ap.getQualificationFileUrl());
      assessorProfile.put("verification_status", ap.getVerificationStatus());
      assessorProfile.put("verified_at", ap.getVerifiedAt());
      map.put("assessor_profile", assessorProfile);
    }

    return map;
  }

  /** Lighter shape used for list responses ({@code GET /users}), adds bio + assessor_verified. */
  public Map<String, Object> toListItem(User user) {
    Map<String, Object> map = base(user);
    AssessorProfile ap = user.getAssessorProfile();
    map.put("bio", ap != null && ap.getEducationBackground() != null ? ap.getEducationBackground() : "-");
    map.put(
        "assessor_verified",
        ap != null
            && ap.getVerificationStatus() != null
            && ap.getVerificationStatus().equalsIgnoreCase("Verified"));
    return map;
  }

  private Map<String, Object> base(User user) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", user.getId());
    map.put("email", user.getEmail());
    map.put("is_active", user.isActive());
    map.put("password_setup_required", user.isPasswordSetupRequired());
    map.put("email_verified_at", user.getEmailVerifiedAt());
    map.put("last_login_at", user.getLastLoginAt());
    map.put("created_at", user.getCreatedAt());
    map.put("updated_at", user.getUpdatedAt());
    map.put("org_unit_id", user.getOrgUnitId());
    map.put("organization", toOrganizationSummary(user.getOrganization()));

    String firstName = user.getEmail().split("@")[0];
    String lastName = "ผู้ใช้งาน";
    String phone = "-";
    if (user.getUserProfile() != null) {
      UserProfile p = user.getUserProfile();
      if (p.getFirstName() != null && !p.getFirstName().isBlank()) firstName = p.getFirstName().trim();
      if (p.getLastName() != null && !p.getLastName().isBlank()) lastName = p.getLastName().trim();
      if (p.getPhone() != null && !p.getPhone().isBlank()) phone = p.getPhone().trim();
    }
    Map<String, Object> profile = new LinkedHashMap<>();
    profile.put("first_name", firstName);
    profile.put("last_name", lastName);
    profile.put("phone", phone);
    profile.put("profile_image", user.getUserProfile() != null ? user.getUserProfile().getProfileImage() : null);
    profile.put(
        "personal_goal_percent",
        user.getUserProfile() != null ? user.getUserProfile().getPersonalGoalPercent() : null);
    map.put("user_profile", profile);

    String primaryRole = user.getRoles().stream().findFirst().map(Role::getRoleName).orElse("User");
    map.put("role", primaryRole);
    map.put("roles", user.getRoles().stream().map(Role::getRoleName).toList());
    map.put("username", firstName);
    return map;
  }

  private Map<String, Object> toOrganizationSummary(Organization org) {
    if (org == null) {
      return null;
    }
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("id", org.getId());
    summary.put("name", org.getName());
    summary.put("industry_type", org.getIndustryType());
    summary.put("current_green_status", org.getCurrentGreenStatus());
    return summary;
  }
}
