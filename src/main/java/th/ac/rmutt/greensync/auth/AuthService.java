package th.ac.rmutt.greensync.auth;

import io.jsonwebtoken.Claims;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import th.ac.rmutt.greensync.auth.dto.LoginRequest;
import th.ac.rmutt.greensync.auth.dto.RegisterAssessorRequest;
import th.ac.rmutt.greensync.auth.dto.RegisterRequest;
import th.ac.rmutt.greensync.common.ApiException;
import th.ac.rmutt.greensync.notifications.MailService;
import th.ac.rmutt.greensync.organizations.Organization;
import th.ac.rmutt.greensync.organizations.OrganizationRepository;
import th.ac.rmutt.greensync.security.JwtService;
import th.ac.rmutt.greensync.users.AssessorProfileData;
import th.ac.rmutt.greensync.users.NewUserData;
import th.ac.rmutt.greensync.users.User;
import th.ac.rmutt.greensync.users.UserRole;
import th.ac.rmutt.greensync.users.UsersService;

@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final UsersService usersService;
  private final OrganizationRepository organizationRepository;
  private final JwtService jwtService;
  private final PasswordEncoder passwordEncoder;
  private final MailService mailService;
  private final String frontendUrl;

  public AuthService(
      UsersService usersService,
      OrganizationRepository organizationRepository,
      JwtService jwtService,
      PasswordEncoder passwordEncoder,
      MailService mailService,
      @Value("${app.frontend-url:http://localhost:4200}") String frontendUrl) {
    this.usersService = usersService;
    this.organizationRepository = organizationRepository;
    this.jwtService = jwtService;
    this.passwordEncoder = passwordEncoder;
    this.mailService = mailService;
    this.frontendUrl = frontendUrl;
  }

  @Transactional
  public Map<String, Object> register(RegisterRequest req) {
    if (usersService.findByEmail(req.userData.email).isPresent()) {
      throw ApiException.conflict("อีเมลนี้ถูกใช้งานแล้ว");
    }

    Organization org = new Organization();
    org.setName(req.orgData.name);
    org.setTaxId(req.orgData.tax_id);
    org.setIndustryType(req.orgData.industry_type);
    org.setNumberOfEmployees(req.orgData.number_of_employees);
    org.setTotalFloorArea(req.orgData.total_floor_area);
    org.setWorkingHoursPerYear(req.orgData.working_hours_per_year);
    org.setBaseYear(req.orgData.base_year);
    org.setTargetReductionPercent(req.orgData.target_reduction_percent);
    org.setCurrentGreenStatus(req.orgData.current_green_status);
    org.setActive(req.orgData.is_active == null || req.orgData.is_active);
    org = organizationRepository.save(org);

    NewUserData newUser =
        new NewUserData(
            req.userData.email,
            req.userData.password,
            org,
            req.userData.firstName,
            req.userData.lastName,
            req.userData.phone,
            UserRole.ORG_ADMIN.roleName(),
            false);
    User user = usersService.create(newUser);

    String token = issueToken(user.getId(), user.getEmail(), org.getId(), UserRole.ORG_ADMIN.roleName());

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("access_token", token);
    result.put(
        "user", Map.of("id", user.getId(), "email", user.getEmail(), "role", UserRole.ORG_ADMIN.roleName()));
    result.put("organization", org);

    String userName = user.getUserProfile() != null && user.getUserProfile().getFirstName() != null
        ? user.getUserProfile().getFirstName()
        : user.getEmail().split("@")[0];
    sendVerificationEmail(user.getId(), user.getEmail(), userName);

    log.info("Registered organization '{}' with admin {}", org.getName(), user.getEmail());
    return result;
  }

  @Transactional
  public Map<String, Object> registerAssessor(RegisterAssessorRequest req) {
    if (usersService.findByEmail(req.userData.email).isPresent()) {
      throw ApiException.conflict("อีเมลนี้ถูกใช้งานแล้ว");
    }

    NewUserData newUser =
        NewUserData.of(req.userData.email, req.userData.password, null, UserRole.ASSESSOR.roleName());
    User user = usersService.create(newUser);

    Integer yearsExperience;
    try {
      yearsExperience = Integer.valueOf(req.profileData.years_experience.trim());
    } catch (NumberFormatException e) {
      throw ApiException.badRequest("จำนวนปีประสบการณ์ต้องเป็นตัวเลข");
    }
    AssessorProfileData profileData =
        new AssessorProfileData(
            req.profileData.firstName,
            req.profileData.lastName,
            req.profileData.phone,
            req.profileData.license_number,
            yearsExperience,
            req.profileData.education_background,
            req.profileData.qualification_file_url,
            req.profileData.bank_name,
            req.profileData.bank_account_no,
            req.profileData.bank_account_name);
    usersService.applyAssessorProfile(user.getId(), profileData);

    Map<String, Object> profile = usersService.getDetail(user.getId());
    String token = issueToken(user.getId(), user.getEmail(), null, UserRole.ASSESSOR.roleName());

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("access_token", token);
    result.put(
        "user", Map.of("id", user.getId(), "email", user.getEmail(), "role", UserRole.ASSESSOR.roleName()));
    result.put("profile", profile);

    sendVerificationEmail(user.getId(), user.getEmail(), req.profileData.firstName);
    log.info("Registered assessor {}", user.getEmail());
    return result;
  }

  @Transactional
  public Map<String, Object> login(LoginRequest req) {
    User user =
        usersService
            .findByEmail(req.email)
            .orElseThrow(() -> ApiException.unauthorized("อีเมลหรือรหัสผ่านไม่ถูกต้อง"));

    if (!passwordEncoder.matches(req.password, user.getPasswordHash())) {
      throw ApiException.unauthorized("อีเมลหรือรหัสผ่านไม่ถูกต้อง");
    }
    if (user.isPasswordSetupRequired()) {
      throw ApiException.unauthorized(
          "บัญชีนี้ต้องตั้งรหัสผ่านก่อนใช้งาน กรุณาเลือก “ลืมรหัสผ่าน” เพื่อรับลิงก์ตั้งรหัสผ่าน");
    }

    String role = usersService.getPrimaryRole(user.getId());
    Integer orgId = user.getOrganization() != null ? user.getOrganization().getId() : null;
    String token = issueToken(user.getId(), user.getEmail(), orgId, role);

    Map<String, Object> userDetail = usersService.getDetail(user.getId());

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("access_token", token);
    Map<String, Object> userPayload = new LinkedHashMap<>();
    userPayload.put("id", user.getId());
    userPayload.put("email", user.getEmail());
    userPayload.put("role", role);
    userPayload.put("username", userDetail.get("username"));
    userPayload.put("user_profile", userDetail.get("user_profile"));
    result.put("user", userPayload);
    result.put("organization", userDetail.get("organization"));

    usersService.updateLastLogin(user.getId());
    return result;
  }

  public Map<String, String> forgotPassword(String email) {
    String genericResponse = "หากอีเมลนี้มีอยู่ในระบบ ลิงก์รีเซ็ตรหัสผ่านจะถูกส่งไปยังอีเมลของคุณ";
    User user = usersService.findByEmail(email).orElse(null);
    if (user == null) {
      log.warn("Forgot password requested for non-existing email: {}", email);
      return Map.of("message", genericResponse);
    }

    byte[] rawTokenBytes = new byte[32];
    SECURE_RANDOM.nextBytes(rawTokenBytes);
    String rawToken = HexFormat.of().formatHex(rawTokenBytes);
    String tokenHash = sha256Hex(rawToken);
    Instant expires = Instant.now().plus(1, ChronoUnit.HOURS);
    usersService.updateResetToken(user.getId(), tokenHash, expires);

    String resetLink = frontendUrl + "/auth/reset-password?token=" + rawToken;
    String userName = user.getUserProfile() != null && user.getUserProfile().getFirstName() != null
        ? user.getUserProfile().getFirstName()
        : user.getEmail().split("@")[0];
    mailService.sendMail(
        user.getEmail(), "รีเซ็ตรหัสผ่าน Green Sync", mailService.resetPasswordTemplate(userName, resetLink));

    log.info("Password reset requested for {}", email);
    return Map.of("message", genericResponse);
  }

  public Map<String, String> resetPassword(String token, String newPassword) {
    if (token == null || token.isBlank() || newPassword == null || newPassword.isBlank()) {
      throw ApiException.badRequest("กรุณาระบุ Token และรหัสผ่านใหม่");
    }
    if (newPassword.length() < 8) {
      throw ApiException.badRequest("รหัสผ่านต้องมีอย่างน้อย 8 ตัวอักษร");
    }

    String tokenHash = sha256Hex(token);
    User user = usersService.findByResetToken(tokenHash).orElseGet(() -> usersService.findByResetToken(token).orElse(null));
    if (user == null) {
      throw ApiException.badRequest("ลิงก์รีเซ็ตรหัสผ่านไม่ถูกต้องหรือหมดอายุแล้ว");
    }
    if (user.getResetPasswordExpires() != null && user.getResetPasswordExpires().isBefore(Instant.now())) {
      throw ApiException.badRequest("ลิงก์รีเซ็ตรหัสผ่านหมดอายุแล้ว กรุณาขอลิงก์ใหม่");
    }

    usersService.updatePasswordAndClearToken(user.getId(), passwordEncoder.encode(newPassword));
    log.info("Password reset successful for user: {}", user.getEmail());
    return Map.of("message", "เปลี่ยนรหัสผ่านเรียบร้อยแล้ว คุณสามารถเข้าสู่ระบบด้วยรหัสผ่านใหม่ได้");
  }

  public Map<String, Object> verifyEmail(String token) {
    if (token == null || token.isBlank()) {
      throw ApiException.badRequest("กรุณาระบุ Token สำหรับการยืนยันอีเมล");
    }
    Claims claims = jwtService.parseClaims(token);
    if (claims == null || !"email-verification".equals(claims.get("purpose"))) {
      throw ApiException.badRequest("ลิงก์ยืนยันอีเมลหมดอายุหรือไม่ถูกต้อง");
    }

    Integer userId = Integer.valueOf(claims.getSubject());
    User user = usersService.findById(userId).orElseThrow(() -> ApiException.notFound("ไม่พบผู้ใช้งานในระบบ"));

    if (user.getEmailVerifiedAt() != null) {
      return Map.of("success", true, "message", "อีเมลได้รับการยืนยันอยู่แล้ว");
    }
    usersService.markEmailVerified(userId);
    log.info("Email verified successfully for user: {}", user.getEmail());
    return Map.of(
        "success", true, "message", "ยืนยันอีเมลสำเร็จแล้ว คุณสามารถเข้าสู่ระบบและเริ่มใช้งานได้");
  }

  private void sendVerificationEmail(Integer userId, String email, String userName) {
    Map<String, Object> claims = new LinkedHashMap<>();
    claims.put("sub", String.valueOf(userId));
    claims.put("email", email);
    claims.put("purpose", "email-verification");
    String token = jwtService.issueToken(claims);
    String verifyLink = frontendUrl + "/auth/verify-email?token=" + token;
    mailService.sendMail(
        email, "ยืนยันอีเมลสำหรับ Green Sync", mailService.verificationEmailTemplate(userName, verifyLink));
  }

  private String issueToken(Integer userId, String email, Integer orgId, String role) {
    Map<String, Object> claims = new LinkedHashMap<>();
    // jjwt enforces the JWT-spec type for the registered "sub" claim (StringOrURI).
    claims.put("sub", String.valueOf(userId));
    claims.put("email", email);
    claims.put("orgId", orgId);
    claims.put("role", role);
    return jwtService.issueToken(claims);
  }

  private static String sha256Hex(String value) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
