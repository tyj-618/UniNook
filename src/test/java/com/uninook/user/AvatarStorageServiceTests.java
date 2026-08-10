package com.uninook.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvatarStorageServiceTests {
    @TempDir
    Path temporaryDirectory;

    @Test
    void storesValidatedAvatarAsManagedPublicResource() throws Exception {
        AvatarStorageProperties properties = new AvatarStorageProperties();
        properties.setAvatarDirectory(temporaryDirectory.toString());
        AvatarStorageService service = new AvatarStorageService(properties);
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", createPngBytes());

        String avatarUrl = service.store(42L, file);

        assertThat(avatarUrl).startsWith("/uploads/avatars/42-").endsWith(".png");
        assertThat(Files.exists(temporaryDirectory.resolve(avatarUrl.substring("/uploads/avatars/".length())))).isTrue();
    }

    @Test
    void rejectsAvatarWhenDeclaredAndActualFormatsDoNotMatch() throws Exception {
        AvatarStorageProperties properties = new AvatarStorageProperties();
        properties.setAvatarDirectory(temporaryDirectory.toString());
        AvatarStorageService service = new AvatarStorageService(properties);
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg", createPngBytes());

        assertThatThrownBy(() -> service.store(42L, file))
                .isInstanceOf(com.uninook.exception.BusinessException.class);
    }

    private byte[] createPngBytes() throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
