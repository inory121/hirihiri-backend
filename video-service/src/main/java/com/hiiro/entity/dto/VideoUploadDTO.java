package com.hiiro.entity.dto;

import lombok.Data;

@Data
public class VideoUploadDTO {
    private String title;
    private String description;
    private String mcId;
    private String scId;
    private String tags;
    private Byte type;
    private Byte auth;
}
