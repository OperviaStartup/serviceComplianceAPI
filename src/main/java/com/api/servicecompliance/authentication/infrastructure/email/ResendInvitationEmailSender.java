package com.api.servicecompliance.authentication.infrastructure.email;

import com.api.servicecompliance.authentication.application.InvitationDeliveryRequested;
import com.api.servicecompliance.authentication.application.InvitationEmailSender;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.core.net.RequestOptions;
import com.resend.services.emails.model.CreateEmailOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class ResendInvitationEmailSender implements InvitationEmailSender {
    private final String apiKey;
    private final String fromEmail;
    private final String invitationBaseUrl;
    private final Resend resend;

    public ResendInvitationEmailSender(@Value("${app.resend.api-key:}") String apiKey,
                                       @Value("${app.resend.from-email:}") String fromEmail,
                                       @Value("${app.invitation.base-url:}") String invitationBaseUrl) {
        this.apiKey = apiKey;
        this.fromEmail = fromEmail;
        this.invitationBaseUrl = invitationBaseUrl;
        this.resend = apiKey.isBlank() ? null : new Resend(apiKey);
    }

    @Override
    public String send(InvitationDeliveryRequested delivery) {
        requireConfiguration();
        String url = invitationBaseUrl + (invitationBaseUrl.contains("?") ? "&" : "?")
                + "token=" + URLEncoder.encode(delivery.token(), StandardCharsets.UTF_8);
        String html = "<p>Hola " + escape(delivery.fullName()) + ",</p>"
                + "<p>Tu usuario de Opervia es <strong>" + escape(delivery.documentNumber()) + "</strong>.</p>"
                + "<p><a href=\"" + escape(url) + "\">Activa tu cuenta y crea tu contraseña</a></p>"
                + "<p>La invitación vence el " + escape(delivery.expiresAt().toString()) + ".</p>";
        CreateEmailOptions email = CreateEmailOptions.builder()
                .from(fromEmail)
                .to(delivery.email())
                .subject("Activa tu cuenta de Opervia")
                .html(html)
                .build();
        try {
            String idempotencyKey = "invitation/" + delivery.invitationId() + "/" + delivery.attempt();
            return resend.emails()
                    .send(email, RequestOptions.builder().setIdempotencyKey(idempotencyKey).build()).getId();
        } catch (ResendException exception) {
            throw new IllegalStateException("Resend rejected invitation delivery", exception);
        }
    }

    private void requireConfiguration() {
        if (apiKey.isBlank() || fromEmail.isBlank() || invitationBaseUrl.isBlank()) {
            throw new IllegalStateException("Resend invitation delivery is not configured");
        }
    }

    private String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
