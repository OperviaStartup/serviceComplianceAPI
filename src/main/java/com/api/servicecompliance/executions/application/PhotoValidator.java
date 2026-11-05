package com.api.servicecompliance.executions.application;

import com.api.servicecompliance.shared.domain.FieldValidationException;
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
    // Bounds the decoded image independently of the compressed upload size.
    private static final long MAX_PIXELS = 25_000_000;

    public ValidatedPhoto validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw invalid("debe contener una fotografía de hasta 5 MB");
        }
        try {
            byte[] data = file.getBytes();
            if (data.length > MAX_BYTES) throw invalid("supera el límite de 5 MB");
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw invalid("debe ser una imagen JPEG o PNG válida");
                var reader = readers.next();
                try {
                    reader.setInput(input);
                    String format = reader.getFormatName();
                    String type = format.equalsIgnoreCase("JPEG") ? "image/jpeg"
                            : format.equalsIgnoreCase("PNG") ? "image/png" : null;
                    if (type == null || !type.equalsIgnoreCase(file.getContentType())) {
                        throw invalid("el contenido y el tipo deben corresponder a JPEG o PNG");
                    }
                    long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                    if (pixels <= 0 || pixels > MAX_PIXELS) throw invalid("las dimensiones de la imagen son excesivas");
                    if (reader.read(0) == null) throw invalid("la imagen está dañada");
                    return new ValidatedPhoto(data, type, type.equals("image/png") ? "png" : "jpg", checksum(data));
                } finally { reader.dispose(); }
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw invalid("no se pudo leer una imagen JPEG o PNG válida");
        }
    }

    public static String checksum(byte[] data) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private FieldValidationException invalid(String message) {
        return new FieldValidationException("Fotografía inválida", Map.of("file", List.of(message)));
    }
    public record ValidatedPhoto(byte[] data, String contentType, String extension, String checksum) {}
}
