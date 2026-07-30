package com.hiiro.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MessageSocketEnvelope {
    private String type;
    private Object data;
}
