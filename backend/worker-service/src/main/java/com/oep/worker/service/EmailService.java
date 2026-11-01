package com.oep.worker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.springframework.beans.factory.annotation.Value;

@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String fromEmail;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${spring.mail.username:noreply@oep.com}") String fromEmail
    ) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
    }

    public void sendForgotPasswordOtp(String to, String fullName, String otpCode) {
        String subject = "[Online Education Platform] Mã xác thực đặt lại mật khẩu của bạn";
        String html    = buildOtpEmailHtml(fullName, otpCode);
        sendHtmlEmail(to, subject, html);
        log.info("OTP email sent to {}", to);
    }

    public void sendPasswordResetSuccessAlert(String to, String fullName, String actionTime) {
        String subject = "[Cảnh báo bảo mật] Mật khẩu tài khoản OEP của bạn đã được thay đổi thành công";
        String html    = buildPasswordResetAlertHtml(fullName, actionTime);
        sendHtmlEmail(to, subject, html);
        log.info("Password-reset alert email sent to {}", to);
    }

    public void sendInstructorApplicationApproved(String to, String fullName, String temporaryPassword, String loginUrl) {
        String subject = "[OEP] Chúc mừng! Hồ sơ giảng viên của bạn đã được phê duyệt thành công";
        String html    = buildInstructorApprovedHtml(fullName, to, temporaryPassword, loginUrl);
        sendHtmlEmail(to, subject, html);
        log.info("Instructor application approved email sent to {}", to);
    }

    public void sendInstructorApplicationRejected(String to, String fullName, String rejectionReason) {
        String subject = "[OEP] Thông báo về kết quả xét duyệt hồ sơ đăng ký giảng viên";
        String html    = buildInstructorRejectedHtml(fullName, rejectionReason);
        sendHtmlEmail(to, subject, html);
        log.info("Instructor application rejected email sent to {}", to);
    }

    private String buildOtpEmailHtml(String fullName, String otpCode) {
        // Format OTP with spaces for readability: "842915" → "8 4 2 9 1 5"
        String otpFormatted = String.join(" ", otpCode.split(""));

        return "<!DOCTYPE html>" +
               "<html lang='vi'>" +
               "<head>" +
               "  <meta charset='UTF-8'>" +
               "  <meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
               "  <title>Mã xác thực OEP</title>" +
               "</head>" +
               "<body style='margin:0;padding:0;font-family:\"Segoe UI\",Arial,sans-serif;background:#f4f6f9;'>" +
               "  <table width='100%' cellpadding='0' cellspacing='0' style='background:#f4f6f9;padding:40px 0;'>" +
               "    <tr><td align='center'>" +
               "      <table width='580' cellpadding='0' cellspacing='0' style='background:#ffffff;border-radius:12px;" +
               "             box-shadow:0 4px 24px rgba(0,0,0,0.08);overflow:hidden;'>" +
               "        <!-- Header -->" +
               "        <tr><td style='background:linear-gradient(135deg,#6366f1 0%,#8b5cf6 100%);" +
               "               padding:32px 40px;text-align:center;'>" +
               "          <h1 style='color:#ffffff;margin:0;font-size:24px;font-weight:700;letter-spacing:-0.5px;'>" +
               "            🎓 Online Education Platform</h1>" +
               "          <p style='color:rgba(255,255,255,0.85);margin:8px 0 0;font-size:14px;'>Bảo mật tài khoản của bạn</p>" +
               "        </td></tr>" +
               "        <!-- Body -->" +
               "        <tr><td style='padding:40px 40px 32px;'>" +
               "          <p style='color:#374151;font-size:16px;margin:0 0 16px;'>Xin chào <strong>" + escapeHtml(fullName) + "</strong>,</p>" +
               "          <p style='color:#6b7280;font-size:15px;line-height:1.6;margin:0 0 28px;'>" +
               "            Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản liên kết với email này." +
               "            Vui lòng sử dụng mã xác thực dưới đây để hoàn tất xác minh:</p>" +
               "          <!-- OTP Box -->" +
               "          <div style='background:linear-gradient(135deg,#f0f4ff 0%,#faf0ff 100%);" +
               "               border:2px solid #e0e7ff;border-radius:12px;padding:28px;text-align:center;margin:0 0 28px;'>" +
               "            <p style='color:#6b7280;font-size:13px;margin:0 0 12px;text-transform:uppercase;" +
               "               letter-spacing:1px;font-weight:600;'>Mã xác thực của bạn</p>" +
               "            <span style='font-size:40px;font-weight:800;letter-spacing:10px;color:#4f46e5;" +
               "               font-family:\"Courier New\",monospace;'>" + otpFormatted + "</span>" +
               "            <p style='color:#9ca3af;font-size:13px;margin:12px 0 0;'>" +
               "              ⏱ Mã này có hiệu lực trong vòng <strong>5 phút</strong> và chỉ sử dụng được <strong>1 lần</strong>.</p>" +
               "          </div>" +
               "          <!-- Security warning -->" +
               "          <div style='background:#fef3c7;border-left:4px solid #f59e0b;border-radius:6px;padding:16px;margin:0 0 24px;'>" +
               "            <p style='color:#92400e;font-size:14px;margin:0;line-height:1.5;'>" +
               "              ⚠️ <strong>Lưu ý bảo mật:</strong> Nếu bạn không yêu cầu đặt lại mật khẩu, xin hãy bỏ qua email này." +
               "              <strong>Tuyệt đối không chia sẻ mã này cho bất kỳ ai.</strong></p>" +
               "          </div>" +
               "        </td></tr>" +
               "        <!-- Footer -->" +
               "        <tr><td style='background:#f9fafb;padding:24px 40px;border-top:1px solid #e5e7eb;text-align:center;'>" +
               "          <p style='color:#9ca3af;font-size:12px;margin:0;'>" +
               "            © 2026 Online Education Platform. Mọi quyền được bảo lưu.<br>" +
               "            Email này được gửi tự động, vui lòng không trả lời trực tiếp.</p>" +
               "        </td></tr>" +
               "      </table>" +
               "    </td></tr>" +
               "  </table>" +
               "</body>" +
               "</html>";
    }

    private String buildPasswordResetAlertHtml(String fullName, String actionTime) {
        return "<!DOCTYPE html>" +
               "<html lang='vi'>" +
               "<head>" +
               "  <meta charset='UTF-8'>" +
               "  <meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
               "  <title>Cảnh báo bảo mật OEP</title>" +
               "</head>" +
               "<body style='margin:0;padding:0;font-family:\"Segoe UI\",Arial,sans-serif;background:#f4f6f9;'>" +
               "  <table width='100%' cellpadding='0' cellspacing='0' style='background:#f4f6f9;padding:40px 0;'>" +
               "    <tr><td align='center'>" +
               "      <table width='580' cellpadding='0' cellspacing='0' style='background:#ffffff;border-radius:12px;" +
               "             box-shadow:0 4px 24px rgba(0,0,0,0.08);overflow:hidden;'>" +
               "        <!-- Header -->" +
               "        <tr><td style='background:linear-gradient(135deg,#dc2626 0%,#b91c1c 100%);" +
               "               padding:32px 40px;text-align:center;'>" +
               "          <h1 style='color:#ffffff;margin:0;font-size:24px;font-weight:700;'>🔒 Cảnh báo Bảo mật</h1>" +
               "          <p style='color:rgba(255,255,255,0.85);margin:8px 0 0;font-size:14px;'>Online Education Platform</p>" +
               "        </td></tr>" +
               "        <!-- Body -->" +
               "        <tr><td style='padding:40px 40px 32px;'>" +
               "          <p style='color:#374151;font-size:16px;margin:0 0 16px;'>Xin chào <strong>" + escapeHtml(fullName) + "</strong>,</p>" +
               "          <p style='color:#6b7280;font-size:15px;line-height:1.6;margin:0 0 24px;'>" +
               "            Chúng tôi xác nhận rằng mật khẩu tài khoản OEP của bạn đã được <strong>thay đổi thành công</strong>.</p>" +
               "          <!-- Info box -->" +
               "          <div style='background:#f0fdf4;border:1px solid #86efac;border-radius:10px;padding:20px;margin:0 0 24px;'>" +
               "            <p style='color:#166534;margin:0;font-size:14px;line-height:1.6;'>" +
               "              ✅ <strong>Thời gian thay đổi:</strong> " + escapeHtml(actionTime != null ? actionTime : "N/A") + "<br>" +
               "              🔑 Tất cả phiên đăng nhập trên các thiết bị khác đã bị thu hồi.<br>" +
               "              🛡️ Tài khoản của bạn hiện được bảo vệ bằng mật khẩu mới.</p>" +
               "          </div>" +
               "          <!-- Warning box -->" +
               "          <div style='background:#fef2f2;border-left:4px solid #dc2626;border-radius:6px;padding:16px;margin:0 0 24px;'>" +
               "            <p style='color:#991b1b;font-size:14px;margin:0;line-height:1.5;'>" +
               "              🚨 <strong>Nếu bạn KHÔNG thực hiện thay đổi này,</strong> tài khoản của bạn có thể đã bị xâm phạm." +
               "              Vui lòng liên hệ ngay với đội ngũ hỗ trợ của chúng tôi để phong tỏa tài khoản.</p>" +
               "          </div>" +
               "          <p style='color:#6b7280;font-size:14px;'>" +
               "            Liên hệ hỗ trợ: <a href='mailto:support@oep.edu.vn' style='color:#4f46e5;'>support@oep.edu.vn</a></p>" +
               "        </td></tr>" +
               "        <!-- Footer -->" +
               "        <tr><td style='background:#f9fafb;padding:24px 40px;border-top:1px solid #e5e7eb;text-align:center;'>" +
               "          <p style='color:#9ca3af;font-size:12px;margin:0;'>" +
               "            © 2026 Online Education Platform. Mọi quyền được bảo lưu.<br>" +
               "            Email này được gửi tự động, vui lòng không trả lời trực tiếp.</p>" +
               "        </td></tr>" +
               "      </table>" +
               "    </td></tr>" +
               "  </table>" +
               "</body>" +
               "</html>";
    }

    private String buildInstructorApprovedHtml(String fullName, String email, String temporaryPassword, String loginUrl) {
        String safeLoginUrl = (loginUrl != null && !loginUrl.isBlank()) ? loginUrl : "http://localhost:3000/login";
        return "<!DOCTYPE html>" +
               "<html lang='vi'>" +
               "<head>" +
               "  <meta charset='UTF-8'>" +
               "  <title>Phê duyệt hồ sơ Giảng viên</title>" +
               "</head>" +
               "<body style='margin:0;padding:0;font-family:\"Segoe UI\",Arial,sans-serif;background:#f8fafc;color:#1e293b;'>" +
               "  <table width='100%' cellpadding='0' cellspacing='0' style='background:#f8fafc;padding:32px 0;'>" +
               "    <tr><td align='center'>" +
               "      <table width='600' cellpadding='0' cellspacing='0' style='background:#ffffff;border-radius:12px;overflow:hidden;border:1px solid #e2e8f0;box-shadow:0 4px 6px -1px rgba(0,0,0,0.05);'>" +
               "        <tr><td style='background:linear-gradient(135deg,#1e3a8a 0%,#3b82f6 100%);padding:36px 32px;text-align:center;color:#ffffff;'>" +
               "          <h1 style='margin:0;font-size:24px;font-weight:700;'>🎓 Nền tảng Giáo dục Trực tuyến OEP</h1>" +
               "          <p style='margin:8px 0 0;opacity:0.9;font-size:15px;'>Thông báo Phê duyệt Đơn đăng ký Giảng viên</p>" +
               "        </td></tr>" +
               "        <tr><td style='padding:32px;'>" +
               "          <p style='font-size:16px;font-weight:600;margin:0 0 16px;'>Kính gửi Thầy/Cô <strong>" + escapeHtml(fullName) + "</strong>,</p>" +
               "          <p style='font-size:15px;line-height:1.6;color:#475569;margin:0 0 24px;'>" +
               "            Ban Quản trị nền tảng OEP trân trọng chúc mừng Thầy/Cô! Hồ sơ đăng ký tham gia đội ngũ giảng dạy của Thầy/Cô đã được ban chuyên môn thẩm định và <strong>chính thức phê duyệt</strong>.</p>" +
               "          <div style='background:#f1f5f9;border-left:4px solid #3b82f6;border-radius:8px;padding:20px;margin-bottom:24px;'>" +
               "            <div style='font-weight:600;color:#1e293b;margin-bottom:12px;'>Thông tin tài khoản đăng nhập:</div>" +
               "            <p style='margin:6px 0;font-size:14px;color:#64748b;'>Tài khoản (Email): <strong style='color:#0f172a;font-family:monospace;background:#e2e8f0;padding:2px 8px;border-radius:4px;'>" + escapeHtml(email) + "</strong></p>" +
               "            <p style='margin:6px 0;font-size:14px;color:#64748b;'>Mật khẩu khởi tạo tạm thời: <strong style='color:#0f172a;font-family:monospace;background:#e2e8f0;padding:2px 8px;border-radius:4px;'>" + escapeHtml(temporaryPassword) + "</strong></p>" +
               "          </div>" +
               "          <div style='background:#fffbeb;border:1px solid #fde68a;border-radius:8px;padding:14px 16px;margin-bottom:28px;font-size:14px;color:#92400e;line-height:1.5;'>" +
               "            ⚠️ <strong>Lưu ý quan trọng:</strong> Vì lý do an toàn bảo mật, hệ thống sẽ <strong>bắt buộc Thầy/Cô đổi mật khẩu mới</strong> ngay trong lần đăng nhập đầu tiên. Vui lòng không chia sẻ mật khẩu tạm thời này với bất kỳ ai." +
               "          </div>" +
               "          <div style='text-align:center;margin-bottom:24px;'>" +
               "            <a href='" + escapeHtml(safeLoginUrl) + "' style='display:inline-block;background:#2563eb;color:#ffffff;text-decoration:none;font-weight:600;font-size:15px;padding:14px 32px;border-radius:8px;' target='_blank'>Đăng nhập vào Hệ thống ngay</a>" +
               "          </div>" +
               "          <p style='margin:0;font-size:14px;color:#64748b;line-height:1.5;'>" +
               "            Nếu Thầy/Cô cần hỗ trợ kỹ thuật hoặc có bất kỳ câu hỏi nào, vui lòng liên hệ Bộ phận Hỗ trợ Giảng viên qua email: <a href='mailto:support@oep.edu.vn' style='color:#2563eb;'>support@oep.edu.vn</a>.</p>" +
               "        </td></tr>" +
               "        <tr><td style='background:#f8fafc;border-top:1px solid #e2e8f0;padding:20px 32px;text-align:center;font-size:13px;color:#94a3b8;'>" +
               "          © 2026 Online Education Platform (OEP). Mọi quyền được bảo lưu.<br>" +
               "          Email này được gửi tự động, vui lòng không phản hồi trực tiếp vào thư này." +
               "        </td></tr>" +
               "      </table>" +
               "    </td></tr>" +
               "  </table>" +
               "</body>" +
               "</html>";
    }

    private String buildInstructorRejectedHtml(String fullName, String rejectionReason) {
        return "<!DOCTYPE html>" +
               "<html lang='vi'>" +
               "<head>" +
               "  <meta charset='UTF-8'>" +
               "  <title>Kết quả xét duyệt hồ sơ</title>" +
               "</head>" +
               "<body style='margin:0;padding:0;font-family:\"Segoe UI\",Arial,sans-serif;background:#f8fafc;color:#1e293b;'>" +
               "  <table width='100%' cellpadding='0' cellspacing='0' style='background:#f8fafc;padding:32px 0;'>" +
               "    <tr><td align='center'>" +
               "      <table width='600' cellpadding='0' cellspacing='0' style='background:#ffffff;border-radius:12px;overflow:hidden;border:1px solid #e2e8f0;box-shadow:0 4px 6px -1px rgba(0,0,0,0.05);'>" +
               "        <tr><td style='background:#475569;padding:32px;text-align:center;color:#ffffff;'>" +
               "          <h1 style='margin:0;font-size:22px;font-weight:700;'>🎓 Nền tảng Giáo dục Trực tuyến OEP</h1>" +
               "          <p style='margin:6px 0 0;opacity:0.9;font-size:14px;'>Thông báo Kết quả Xét duyệt Hồ sơ</p>" +
               "        </td></tr>" +
               "        <tr><td style='padding:32px;'>" +
               "          <p style='font-size:16px;font-weight:600;margin:0 0 16px;'>Kính gửi Thầy/Cô <strong>" + escapeHtml(fullName) + "</strong>,</p>" +
               "          <p style='font-size:15px;line-height:1.6;color:#475569;margin:0 0 20px;'>" +
               "            Ban Quản trị nền tảng OEP xin chân thành cảm ơn sự quan tâm của Thầy/Cô đối với việc hợp tác phát triển nội dung giảng dạy cùng chúng tôi.</p>" +
               "          <p style='font-size:15px;line-height:1.6;color:#475569;margin:0 0 20px;'>" +
               "            Sau khi xem xét kỹ lưỡng hồ sơ đăng ký, chúng tôi rất tiếc phải thông báo rằng hồ sơ của Thầy/Cô <strong>chưa phù hợp với tiêu chuẩn xét duyệt hiện tại</strong> của nền tảng.</p>" +
               "          <div style='background:#fef2f2;border-left:4px solid #ef4444;border-radius:8px;padding:18px;margin-bottom:24px;'>" +
               "            <div style='font-weight:600;color:#991b1b;font-size:14px;margin-bottom:8px;'>Chi tiết lý do từ Ban Quản trị:</div>" +
               "            <div style='font-size:14px;color:#b91c1c;line-height:1.5;font-style:italic;'>" +
               "              \"" + escapeHtml(rejectionReason != null ? rejectionReason : "Hồ sơ chưa đạt tiêu chuẩn yêu cầu.") + "\"" +
               "            </div>" +
               "          </div>" +
               "          <p style='font-size:14px;color:#475569;line-height:1.6;margin:0 0 16px;'>" +
               "            Thầy/Cô hoàn toàn có thể cập nhật, bổ sung thêm các thông tin năng lực hoặc kinh nghiệm và gửi lại đơn đăng ký mới trên cổng thông tin của chúng tôi khi đã hoàn thiện hồ sơ.</p>" +
               "          <p style='font-size:14px;color:#64748b;margin:0;'>" +
               "            Kính chúc Thầy/Cô luôn dồi dào sức khỏe và đạt nhiều thành công trong sự nghiệp giáo dục.</p>" +
               "        </td></tr>" +
               "        <tr><td style='background:#f8fafc;border-top:1px solid #e2e8f0;padding:20px 32px;text-align:center;font-size:13px;color:#94a3b8;'>" +
               "          © 2026 Online Education Platform (OEP). Mọi thắc mắc vui lòng gửi về: support@oep.edu.vn" +
               "        </td></tr>" +
               "      </table>" +
               "    </td></tr>" +
               "  </table>" +
               "</body>" +
               "</html>";
    }

    private void sendHtmlEmail(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            try {
                helper.setFrom(fromEmail, "Online Education Platform");
            } catch (Exception ignored) {
                helper.setFrom(fromEmail);
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Email send failure", e);
        }
    }

    /** Basic HTML escaping to prevent XSS in email templates. */
    private String escapeHtml(String input) {
        if (input == null) return "";
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}
