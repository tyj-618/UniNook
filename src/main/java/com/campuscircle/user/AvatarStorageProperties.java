package com.campuscircle.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "campuscircle.storage")
public class AvatarStorageProperties {

    private String avatarDirectory = "./data/uploads/avatars";

    public String getAvatarDirectory() {
        return avatarDirectory;
    }

    public void setAvatarDirectory(String avatarDirectory) {
        this.avatarDirectory = avatarDirectory;
    }
}
