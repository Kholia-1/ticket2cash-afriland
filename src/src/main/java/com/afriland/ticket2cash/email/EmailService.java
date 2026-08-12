package com.afriland.ticket2cash.email;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Service
public class EmailService {

    private final SmtpConfigRepository repo;

    public EmailService(SmtpConfigRepository repo) {
        this.repo = repo;
    }

    public SmtpConfig currentConfig() {
        return repo.findAll().stream().findFirst().orElse(null);
    }

    public boolean isReady() {
        SmtpConfig c = currentConfig();
        return c != null
                && Boolean.TRUE.equals(c.getEnabled())
                && c.getHost() != null && !c.getHost().isBlank();
    }

    private JavaMailSenderImpl buildSender(SmtpConfig c) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(c.getHost());
        if (c.getPort() != null) sender.setPort(c.getPort());
        if (c.getUsername() != null) sender.setUsername(c.getUsername());
        if (c.getPassword() != null) sender.setPassword(c.getPassword());

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", (c.getUsername() != null && !c.getUsername().isBlank()) ? "true" : "false");
        props.put("mail.smtp.starttls.enable", Boolean.TRUE.equals(c.getUseTls()) ? "true" : "false");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        return sender;
    }

    public void send(String to, String subject, String body) {
        SmtpConfig c = currentConfig();
        if (c == null) throw new IllegalStateException("SMTP not configured");
        if (!Boolean.TRUE.equals(c.getEnabled())) throw new IllegalStateException("SMTP is disabled");
        if (c.getHost() == null || c.getHost().isBlank()) throw new IllegalStateException("SMTP host missing");

        JavaMailSenderImpl sender = buildSender(c);
        SimpleMailMessage msg = new SimpleMailMessage();
        String from = (c.getFromAddress() != null && !c.getFromAddress().isBlank())
                ? c.getFromAddress() : c.getUsername();
        if (from != null) msg.setFrom(from);
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(body);
        sender.send(msg);
    }

    public void sendCredentials(String to, String name, String username, String password, String loginUrl) {
        String subject = "Vos acces Ticket2Cash";
        StringBuilder b = new StringBuilder();
        b.append("Bonjour,\n\n");
        b.append("Votre espace partenaire Ticket2Cash");
        if (name != null && !name.isBlank()) b.append(" (").append(name).append(")");
        b.append(" est pret.\n\n");
        if (loginUrl != null && !loginUrl.isBlank()) b.append("Lien de connexion : ").append(loginUrl).append("\n");
        b.append("Identifiant : ").append(username).append("\n");
        b.append("Mot de passe : ").append(password).append("\n\n");
        b.append("Merci de changer votre mot de passe apres la premiere connexion.\n\n");
        b.append("Ticket2Cash - Afriland First Bank");
        send(to, subject, b.toString());
    }
}
