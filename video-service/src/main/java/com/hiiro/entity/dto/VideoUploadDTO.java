package com.hiiro.entity.dto;

import lombok.Data;

@Data
public class VideoUploadDTO {
    private String title;
    private String descr;
    private Double duration;
    private String mcId;
    private String scId;
    private String tags;
    private Byte type;
    private Byte auth;
}