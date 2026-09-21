package th.ac.rmutt.greensync.users;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import th.ac.rmutt.greensync.common.ApiException;
import th.ac.rmutt.greensync.users.dto.CreateUserRequest;
import th.ac.rmutt.greensync.users.dto.UpdateProfileRequest;
import th.ac.rmutt.greensync.users.dto.UpdateUserRequest;

@Service
public class UsersService {

  private final UserRepository userRepository;
  private final UserProfileRepository userProfileRepository;
  private final AssessorProfileRepository assessorProfileRepository;
  private final RoleRepository roleRepository;
  private final BankAccountRepository bankAccountRepository;
  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;

  public UsersService(
      UserRepository userRepository,
      UserProfileRepository userProfileRepository,
      AssessorProfileRepository assessorProfileRepository,
      RoleRepository roleRepository,
      BankAccountRepository bankAccountRepository,
      UserMapper userMapper,
      PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.userProfileRepository = userProfileRepository;
    this.assessorProfileRepository = assessorProfileRepository;
    this.roleRepository = roleRepository;
    this.bankAccountRepository = bankAccountRepository;
    this.userMapper = userMapper;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional(readOnly = true)
  public Optional<User> findByEmail(String email) {
    return userRepository.findByEmailIgnoreCase(email);
  }

  @Transactional(readOnly = true)
  public Optional<User> findById(Integer id) {
    return userRepository.findById(id);
  }

  @Transactional(readOnly = true)
  public Optional<User> findOrgAdmin(Integer orgId) {
    return userRepository.search(orgId, "ORG_ADMIN", org.springframework.data.domain.PageRequest.of(0, 1))
        .stream()
        .findFirst();
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getDetail(Integer id) {
    User user = userRepository.findById(id).orElseThrow(() -> ApiException.notFound("ไม่พบผู้ใช้งานในระบบ"));
    Map<String, Object> detail = userMapper.toDetail(user);
    if (user.getAssessorProfile() != null) {
      Optional<BankAccount> primaryBank = bankAccountRepository.findFirstByUserId(id);
      if (primaryBank.isPresent()) {
        @SuppressWarnings("unchecked")
        Map<String, Object> assessorProfile = (Map<String, Object>) detail.get("assessor_profile");
        assessorProfile.put("bank_name", primaryBank.get().getBankName());
        assessorProfile.put("bank_account_no", primaryBank.get().getAccountNo());
      }
    }
    return detail;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> findAll(String roleFilter, Integer orgId, Integer page, Integer limit) {
    int safeLimit = limit != null ? Math.min(Math.max(1, limit), 200) : 50;
    int safePage = page != null ? Math.max(1, page) : 1;
    Pageable pageable = PageRequest.of(safePage - 1, safeLimit, Sort.by(Sort.Direction.DESC, "createdAt"));

    List<User> users;
    if ("ASSESSOR".equalsIgnoreCase(roleFilter)) {
      users = userRepository.searchByAnyRole(orgId, List.of("ASSESSOR"), pageable);
    } else {
      String normalizedRole = roleFilter != null ? roleFilter.toUpperCase() : null;
      users = userRepository.search(orgId, normalizedRole, pageable);
    }
    return users.stream().map(userMapper::toListItem).toList();
  }

  @Transactional
  public User create(NewUserData data) {
    if (userRepository.findByEmailIgnoreCase(data.email()).isPresent()) {
      throw ApiException.conflict("อีเมลนี้ถูกใช้งานแล้ว");
    }

    User user = new User();
    user.setEmail(data.email());
    user.setPasswordHash(passwordEncoder.encode(data.rawPassword()));
    user.setActive(true);
    user.setPasswordSetupRequired(data.passwordSetupRequired());
    user.setOrganization(data.organization());

    UserProfile profile = new UserProfile();
    profile.setUser(user);
    profile.setFirstName(data.firstName() != null ? data.firstName() : data.email().split("@")[0]);
    profile.setLastName(data.lastName() != null ? data.lastName() : "ใหม่");
    profile.setPhone(data.phone() != null ? data.phone() : "-");
    user.setUserProfile(profile);

    user = userRepository.save(user);
    assignRole(user.getId(), data.role() != null ? data.role() : UserRole.USER.roleName());
    return userRepository.findById(user.getId()).orElseThrow();
  }

  @Transactional
  public User createFromRequest(CreateUserRequest request, th.ac.rmutt.greensync.organizations.Organization org) {
    NewUserData data =
        new NewUserData(
            request.email,
            request.password,
            org,
            request.user_profile != null ? request.user_profile.first_name : null,
            request.user_profile != null ? request.user_profile.last_name : null,
            request.user_profile != null ? request.user_profile.phone : null,
            request.role != null ? request.role : UserRole.USER.roleName(),
            false);
    return create(data);
  }

  @Transactional
  public void assignRole(Integer userId, String roleName) {
    Role role = ensureRoleExists(roleName);
    User user = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("ไม่พบผู้ใช้งานในระบบ"));
    if (user.getRoles().stream().noneMatch(r -> r.getId().equals(role.getId()))) {
      user.getRoles().add(role);
      userRepository.save(user);
    }
  }

  @Transactional
  public void setRole(Integer userId, String roleName) {
    User user = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("ไม่พบผู้ใช้งานในระบบ"));
    user.getRoles().clear();
    userRepository.save(user);
    assignRole(userId, roleName);
  }

  @Transactional
  public Role ensureRoleExists(String roleName) {
    return roleRepository
        .findByRoleNameIgnoreCase(roleName)
        .orElseGet(() -> roleRepository.save(new Role(roleName)));
  }

  @Transactional(readOnly = true)
  public List<Role> getAllRoles() {
    return roleRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
  }

  @Transactional(readOnly = true)
  public String getPrimaryRole(Integer userId) {
    User user = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("ไม่พบผู้ใช้งานในระบบ"));
    List<String> roleNames = user.getRoles().stream().map(r -> r.getRoleName().toUpperCase()).toList();
    if (roleNames.isEmpty()) return UserRole.USER.roleName();
    if (roleNames.contains(UserRole.SYSTEM_ADMIN.roleName().toUpperCase())) return UserRole.SYSTEM_ADMIN.roleName();
    if (roleNames.contains(UserRole.ORG_ADMIN.roleName().toUpperCase()) || roleNames.contains("ORG_ADMIN"))
      return UserRole.ORG_ADMIN.roleName();
    if (roleNames.contains(UserRole.ADMIN.roleName().toUpperCase())) return UserRole.SYSTEM_ADMIN.roleName();
    if (roleNames.contains(UserRole.ASSESSOR.roleName().toUpperCase())) return UserRole.ASSESSOR.roleName();
    if (roleNames.contains(UserRole.EXECUTIVE.roleName().toUpperCase())) return UserRole.EXECUTIVE.roleName();
    if (roleNames.contains(UserRole.EMPLOYEE.roleName().toUpperCase())) return UserRole.EMPLOYEE.roleName();
    return UserRole.USER.roleName();
  }

  @Transactional
  public User update(Integer id, UpdateUserRequest req) {
    User user = userRepository.findById(id).orElseThrow(() -> ApiException.notFound("ไม่พบผู้ใช้งานในระบบ"));

    if (req.email != null) user.setEmail(req.email);
    if (req.is_active != null) user.setActive(req.is_active);
    if (req.password != null && !req.password.isBlank()) {
      user.setPasswordHash(passwordEncoder.encode(req.password));
      user.setPasswordSetupRequired(false);
    }
    if (req.org_unit_id != null) user.setOrgUnitId(req.org_unit_id);
    userRepository.save(user);

    if (req.role != null) {
      setRole(id, req.role);
    }

    if (req.user_profile != null) {
      UserProfile profile = userProfileRepository.findByUserId(id).orElseGet(() -> {
        UserProfile p = new UserProfile();
        p.setUser(user);
        return p;
      });
      if (req.user_profile.first_name != null) profile.setFirstName(req.user_profile.first_name);
      if (req.user_profile.last_name != null) profile.setLastName(req.user_profile.last_name);
      if (req.user_profile.phone != null) profile.setPhone(req.user_profile.phone);
      if (req.user_profile.profile_image != null) profile.setProfileImage(req.user_profile.profile_image);
      userProfileRepository.save(profile);
    }

    return userRepository.findById(id).orElseThrow();
  }

  @Transactional
  public User updateProfileOnly(Integer id, UpdateProfileRequest req) {
    User user = userRepository.findById(id).orElseThrow(() -> ApiException.notFound("ไม่พบผู้ใช้งานในระบบ"));
    UserProfile profile =
        userProfileRepository.findByUserId(id).orElseGet(() -> {
          UserProfile p = new UserProfile();
          p.setUser(user);
          return p;
        });
    if (req.first_name != null) profile.setFirstName(req.first_name);
    if (req.last_name != null) profile.setLastName(req.last_name);
    if (req.phone != null) profile.setPhone(req.phone);
    if (req.profile_image != null) profile.setProfileImage(req.profile_image);
    userProfileRepository.save(profile);

    if (req.bio != null) {
      assessorProfileRepository.findByUserId(id).ifPresent(ap -> {
        ap.setEducationBackground(req.bio);
        assessorProfileRepository.save(ap);
      });
    }

    if (req.bank_account != null) {
      BankAccount account = bankAccountRepository.findFirstByUserId(id).orElseGet(() -> {
        BankAccount a = new BankAccount();
        a.setUser(user);
        a.setPrimary(true);
        return a;
      });
      if (req.bank_account.bank_name != null) account.setBankName(req.bank_account.bank_name);
      if (req.bank_account.account_no != null) account.setAccountNo(req.bank_account.account_no);
      if (req.bank_account.account_name != null) account.setAccountName(req.bank_account.account_name);
      bankAccountRepository.save(account);
    }

    return userRepository.findById(id).orElseThrow();
  }

  @Transactional
  public void setPersonalGoal(Integer userId, java.math.BigDecimal targetReductionPercent) {
    User user = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("ไม่พบผู้ใช้งานในระบบ"));
    UserProfile profile =
        userProfileRepository.findByUserId(userId).orElseGet(() -> {
          UserProfile p = new UserProfile();
          p.setUser(user);
          p.setFirstName("ผู้ใช้งาน");
          p.setLastName("ใหม่");
          p.setPhone("-");
          return p;
        });
    profile.setPersonalGoalPercent(targetReductionPercent);
    userProfileRepository.save(profile);
  }

  @Transactional
  public void remove(Integer id) {
    userRepository.deleteById(id);
  }

  @Transactional
  public void updateResetToken(Integer userId, String tokenHash, Instant expires) {
    User user = userRepository.findById(userId).orElseThrow();
    user.setResetPasswordToken(tokenHash);
    user.setResetPasswordExpires(expires);
    userRepository.save(user);
  }

  @Transactional(readOnly = true)
  public Optional<User> findByResetToken(String token) {
    return userRepository.findByResetPasswordToken(token);
  }

  @Transactional
  public void updatePasswordAndClearToken(Integer userId, String hashedPassword) {
    User user = userRepository.findById(userId).orElseThrow();
    user.setPasswordHash(hashedPassword);
    user.setResetPasswordToken(null);
    user.setResetPasswordExpires(null);
    user.setPasswordSetupRequired(false);
    userRepository.save(user);
  }

  @Transactional
  public void markEmailVerified(Integer userId) {
    User user = userRepository.findById(userId).orElseThrow();
    user.setEmailVerifiedAt(Instant.now());
    userRepository.save(user);
  }

  @Transactional
  public void updateLastLogin(Integer userId) {
    User user = userRepository.findById(userId).orElseThrow();
    user.setLastLoginAt(Instant.now());
    userRepository.save(user);
  }

  /** Fills in the profile, assessor profile, and bank account captured on self-registration. */
  @Transactional
  public void applyAssessorProfile(Integer userId, AssessorProfileData data) {
    User user = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("ไม่พบผู้ใช้งานในระบบ"));

    UserProfile profile = userProfileRepository.findByUserId(userId).orElseGet(() -> {
      UserProfile p = new UserProfile();
      p.setUser(user);
      return p;
    });
    profile.setFirstName(data.firstName());
    profile.setLastName(data.lastName());
    profile.setPhone(data.phone());
    userProfileRepository.save(profile);

    AssessorProfile assessorProfile =
        assessorProfileRepository.findByUserId(userId).orElseGet(() -> {
          AssessorProfile ap = new AssessorProfile();
          ap.setUser(user);
          return ap;
        });
    assessorProfile.setLicenseNumber(data.licenseNumber());
    assessorProfile.setYearsExperience(data.yearsExperience());
    assessorProfile.setEducationBackground(data.educationBackground());
    assessorProfile.setQualificationFileUrl(data.qualificationFileUrl());
    assessorProfile.setVerificationStatus("Pending");
    assessorProfileRepository.save(assessorProfile);

    if (data.bankName() != null || data.bankAccountNo() != null || data.bankAccountName() != null) {
      BankAccount account = bankAccountRepository.findFirstByUserId(userId).orElseGet(() -> {
        BankAccount a = new BankAccount();
        a.setUser(user);
        a.setPrimary(true);
        return a;
      });
      account.setBankName(data.bankName());
      account.setAccountNo(data.bankAccountNo());
      account.setAccountName(data.bankAccountName());
      bankAccountRepository.save(account);
    }
  }

  /**
   * Imports employees from a two-or-more-column CSV (email,firstName,lastName,phone). Each
   * account gets a random bootstrap password that is never returned or logged — the owner sets
   * their real password through the existing forgot-password flow. Rows with an email that
   * already exists are skipped.
   */
  @Transactional
  public int bulkImportUsers(th.ac.rmutt.greensync.organizations.Organization org, String csvContent) {
    String[] lines = csvContent.split("\\R");
    int imported = 0;
    for (int i = 1; i < lines.length; i++) { // skip header row
      String line = lines[i].trim();
      if (line.isEmpty()) continue;
      String[] cols = line.split(",", -1);
      String email = cols[0].trim();
      if (email.isEmpty() || userRepository.findByEmailIgnoreCase(email).isPresent()) continue;

      String firstName = cols.length > 1 && !cols[1].isBlank() ? cols[1].trim() : email.split("@")[0];
      String lastName = cols.length > 2 && !cols[2].isBlank() ? cols[2].trim() : "Employee";
      String phone = cols.length > 3 && !cols[3].isBlank() ? cols[3].trim() : "-";

      byte[] secretBytes = new byte[32];
      new java.security.SecureRandom().nextBytes(secretBytes);
      String bootstrapSecret = java.util.HexFormat.of().formatHex(secretBytes);

      NewUserData data =
          new NewUserData(email, bootstrapSecret, org, firstName, lastName, phone, UserRole.EMPLOYEE.roleName(), true);
      create(data);
      imported++;
    }
    return imported;
  }
}
