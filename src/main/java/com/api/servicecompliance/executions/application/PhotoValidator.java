package com.api.servicecompliance.executions.application;

import com.api.servicecompliance.shared.domain.FieldValidationException;
import com.api.servicecompliance.executions.domain.model.EvidenceType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Component
public class PhotoValidator {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    public static final int SIGNATURE_MAX_BYTES = 1024 * 1024;
    // Bounds the decoded image independently of the compressed upload size.
    private static final long MAX_PIXELS = 25_000_000;

    public ValidatedPhoto validate(MultipartFile file) {
        return validate(file, EvidenceType.PHOTO);
    }

    public ValidatedPhoto validate(MultipartFile file, EvidenceType evidenceType) {
        if (evidenceType != EvidenceType.PHOTO && evidenceType != EvidenceType.SIGNATURE) {
            throw invalid("file", "tipo de evidencia binaria inválido");
        }
        int limit = evidenceType == EvidenceType.SIGNATURE ? SIGNATURE_MAX_BYTES : MAX_BYTES;
        if (file == null || file.isEmpty() || file.getSize() > limit) {
            throw invalid("file", evidenceType == EvidenceType.SIGNATURE
                    ? "debe contener una firma PNG de hasta 1 MB"
                    : "debe contener una fotografía de hasta 5 MB");
        }
        try {
            byte[] data = file.getBytes();
            if (data.length > limit) throw invalid("file", "supera el límite permitido");
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw invalid("file", "debe ser una imagen JPEG o PNG válida");
                var reader = readers.next();
                try {
                    reader.setInput(input);
                    String format = reader.getFormatName();
                    String contentType = format.equalsIgnoreCase("JPEG") ? "image/jpeg"
                            : format.equalsIgnoreCase("PNG") ? "image/png" : null;
                    if (contentType == null || !contentType.equalsIgnoreCase(file.getContentType())
                            || (evidenceType == EvidenceType.SIGNATURE && !"image/png".equals(contentType))) {
                        throw invalid("file", evidenceType == EvidenceType.SIGNATURE
                                ? "el contenido y el tipo deben corresponder a PNG"
                                : "el contenido y el tipo deben corresponder a JPEG o PNG");
                    }
                    long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                    if (pixels <= 0 || pixels > MAX_PIXELS) {
                        throw invalid("file", "las dimensiones de la imagen son excesivas");
                    }
                    if (reader.read(0) == null) throw invalid("file", "la imagen está dañada");
                    return new ValidatedPhoto(data, contentType,
                            contentType.equals("image/png") ? "png" : "jpg", checksum(data));
                } finally { reader.dispose(); }
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw invalid("file", "no se pudo leer una imagen JPEG o PNG válida");
        }
    }

    public static String checksum(byte[] data) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private FieldValidationException invalid(String field, String message) {
        return new FieldValidationException("Evidencia inválida", Map.of(field, List.of(message)));
    }

    public record ValidatedPhoto(byte[] data, String contentType, String extension, String checksum) {}
}
