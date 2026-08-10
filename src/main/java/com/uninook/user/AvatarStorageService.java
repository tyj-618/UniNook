package com.uninook.user;

import com.uninook.common.ErrorCode;
import com.uninook.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

@Service
public class AvatarStorageService {

    private static final long MAX_AVATAR_SIZE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_IMAGE_DIMENSION = 4096;
    private static final long MAX_IMAGE_PIXELS = 16_000_000L;
    private static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");
    private static final String PUBLIC_URL_PREFIX = "/uploads/avatars/";

    private final Path avatarDirectory;

    public AvatarStorageService(AvatarStorageProperties properties) {
        this.avatarDirectory = Path.of(properties.getAvatarDirectory()).toAbsolutePath().normalize();
    }

    public String store(Long userId, MultipartFile file) {
        String format = validate(file);
        String filename = userId + "-" + UUID.randomUUID() + extensionOf(format);
        Path target = avatarDirectory.resolve(filename).normalize();
        if (!target.startsWith(avatarDirectory)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "头像文件路径无效");
        }

        try {
            Files.createDirectories(avatarDirectory);
            try (InputStream input = file.getInputStream(); OutputStream output = Files.newOutputStream(target)) {
                BufferedImage image = ImageIO.read(input);
                if (image == null || !ImageIO.write(image, format, output)) {
                    throw new IOException("Unsupported image data");
                }
            }
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "头像保存失败，请稍后重试");
        }
        return PUBLIC_URL_PREFIX + filename;
    }

    public void deleteIfManaged(String avatarUrl) {
        if (avatarUrl == null || !avatarUrl.startsWith(PUBLIC_URL_PREFIX)) {
            return;
        }
        Path target = avatarDirectory.resolve(avatarUrl.substring(PUBLIC_URL_PREFIX.length())).normalize();
        if (!target.startsWith(avatarDirectory)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // The new avatar has already been persisted. An orphaned old file can be cleaned up later.
        }
    }

    private String validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择头像图片");
        }
        if (file.getSize() > MAX_AVATAR_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "头像图片不能超过 2MB");
        }
        if (!SUPPORTED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "头像仅支持 PNG 或 JPEG 格式");
        }
        try (InputStream input = file.getInputStream(); ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
            if (imageInput == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "上传文件不是有效图片");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "上传文件不是有效图片");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                String format = reader.getFormatName().toLowerCase();
                if (!isMatchingFormat(file.getContentType(), format)) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR, "头像文件内容与声明格式不一致");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION
                        || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR, "头像图片尺寸过大");
                }
                return format;
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "头像图片无法读取");
        }
    }

    private boolean isMatchingFormat(String contentType, String format) {
        return ("image/png".equals(contentType) && "png".equals(format))
                || ("image/jpeg".equals(contentType) && ("jpeg".equals(format) || "jpg".equals(format)));
    }

    private String extensionOf(String format) {
        return "png".equals(format) ? ".png" : ".jpg";
    }
}
