package com.squad.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${mail.from}")
    private String fromEmail;

    public boolean sendEmail(String to, String subject, String message, Map<String, String> templateData) {
        try {
            // Validate email format
            if (to == null || to.trim().isEmpty() || !to.contains("@")) {
                log.error("Invalid email address: {}", to);
                throw new IllegalArgumentException("Invalid email address: " + to);
            }
            
            String htmlContent = buildEmailHtml(templateData, message);
            
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
            
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(message, htmlContent);
            
            mailSender.send(mimeMessage);
            log.info("Email sent successfully to: {}", to);
            return true;
        } catch (MessagingException | IOException e) {
            log.error("Error sending email to {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error sending email to {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }

    public boolean sendSimpleEmail(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            
            mailSender.send(message);
            log.info("Simple email sent successfully to: {}", to);
            return true;
        } catch (Exception e) {
            log.error("Error sending simple email to {}: {}", to, e.getMessage(), e);
            return false;
        }
    }

    private String buildEmailHtml(Map<String, String> templateData, String defaultMessage) throws IOException {
        ClassPathResource resource = new ClassPathResource("templates/emailTemplate.html");
        String template = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        
        String emailTitle = templateData.getOrDefault("emailTitle", "Squad STM");
        String emailHeading = templateData.getOrDefault("emailHeading", "Squad STM");
        String emailMessage = templateData.getOrDefault("emailMessage", defaultMessage);
        String buttonText = templateData.getOrDefault("buttonText", "Click Here");
        String buttonLink = templateData.getOrDefault("buttonLink", "#");
        String buttonColor = templateData.getOrDefault("buttonColor", "#007bff");
        String additionalInfo = templateData.getOrDefault("additionalInfo", "");
        String footerMessage = templateData.getOrDefault("footerMessage", "Thank you for using Squad STM");
        String currentYear = String.valueOf(java.time.Year.now().getValue());
        
        template = template.replace("{{emailTitle}}", emailTitle);
        template = template.replace("{{emailHeading}}", emailHeading);
        template = template.replace("{{{emailMessage}}}", emailMessage);
        template = template.replace("{{emailMessage}}", emailMessage);
        template = template.replace("{{buttonText}}", buttonText);
        template = template.replace("{{buttonLink}}", buttonLink);
        template = template.replace("{{buttonColor}}", buttonColor);
        template = template.replace("{{footerMessage}}", footerMessage);
        template = template.replace("{{currentYear}}", currentYear);
        
        if (additionalInfo == null || additionalInfo.isEmpty()) {
            template = template.replaceAll("(?s)<!-- Additional Info Section -->.*?\\{\\{/if\\}\\}", "");
        } else {
            template = template.replace("{{additionalInfo}}", additionalInfo);
            template = template.replace("{{#if additionalInfo}}", "");
            template = template.replace("{{/if}}", "");
        }
        
        return template;
    }
}
