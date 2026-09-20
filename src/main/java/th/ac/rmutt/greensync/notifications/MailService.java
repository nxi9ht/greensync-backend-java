package th.ac.rmutt.greensync.notifications;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Sends transactional email. Mirrors the NestJS MailService's mock/live split: with no SMTP
 * credentials configured, mail is just logged instead of sent, so local dev never needs a real
 * mailbox. The settings-table-driven "smtp.mode" toggle from the NestJS version is not ported
 * yet (the settings module itself isn't ported), so mode is decided purely from env vars here.
 */
@Service
public class MailService {

  private static final Logger log = LoggerFactory.getLogger(MailService.class);

  private final JavaMailSender mailSender;
  private final String smtpUser;
  private final boolean liveModeConfigured;

  public MailService(
      JavaMailSender mailSender,
      @Value("${spring.mail.host:}") String host,
      @Value("${spring.mail.username:}") String smtpUser,
      @Value("${spring.mail.password:}") String smtpPass) {
    this.mailSender = mailSender;
    this.smtpUser = smtpUser;
    this.liveModeConfigured =
        !host.isBlank() && !host.equals("smtp.example.com") && !smtpUser.isBlank() && !smtpPass.isBlank();
    log.info(
        liveModeConfigured
            ? "MailService initialized in LIVE mode ({})"
            : "MailService initialized in MOCK mode (no live SMTP credentials configured)",
        host);
  }

  public void sendMail(String to, String subject, String html) {
    if (!liveModeConfigured) {
      log.info("[MOCK EMAIL] To: {} | Subject: {}", to, subject);
      return;
    }
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
      helper.setFrom(smtpUser);
      helper.setTo(to);
      helper.setSubject(subject);
      helper.setText(html, true);
      mailSender.send(message);
      log.info("[LIVE] Email sent to {}", to);
    } catch (Exception e) {
      log.error("[LIVE] Failed to send email to {}: {}", to, e.getMessage());
    }
  }

  public String resetPasswordTemplate(String userName, String resetLink) {
    return """
        <div style="font-family:'Segoe UI',Arial,sans-serif;max-width:520px;margin:0 auto;background:#fff;border-radius:16px;overflow:hidden;border:1px solid #e2e8f0;">
          <div style="background:linear-gradient(135deg,#059669,#0d9488);padding:32px;text-align:center;">
            <h1 style="color:white;margin:0;font-size:22px;font-weight:800;">รีเซ็ตรหัสผ่าน</h1>
          </div>
          <div style="padding:32px;">
            <p style="color:#334155;font-size:15px;">สวัสดีครับ <strong>%s</strong>,</p>
            <p style="color:#64748b;font-size:14px;">เราได้รับคำขอรีเซ็ตรหัสผ่านสำหรับบัญชีของคุณ</p>
            <div style="text-align:center;margin:28px 0;">
              <a href="%s" style="display:inline-block;padding:14px 36px;background:linear-gradient(135deg,#059669,#0d9488);color:white;text-decoration:none;border-radius:12px;font-weight:700;">ตั้งรหัสผ่านใหม่</a>
            </div>
            <p style="color:#94a3b8;font-size:12px;">ลิงก์นี้จะหมดอายุใน 1 ชั่วโมง</p>
          </div>
        </div>
        """
        .formatted(userName, resetLink);
  }

  public String verificationEmailTemplate(String userName, String verifyLink) {
    return """
        <div style="font-family:Arial,sans-serif;color:#333;">
          <h2>ยินดีต้อนรับสู่ Green Sync, %s</h2>
          <p>กรุณายืนยันอีเมลของคุณเพื่อเริ่มต้นใช้งานระบบ</p>
          <a href="%s" style="padding:10px 15px;background-color:#28a745;color:white;text-decoration:none;border-radius:5px;">ยืนยันอีเมล</a>
        </div>
        """
        .formatted(userName, verifyLink);
  }
}
