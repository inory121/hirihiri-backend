package com.hiiro.exp;

import java.io.Serial;

public class VideoNotFoundException extends Exception {
    @Serial
    private static final long serialVersionUID = 1L;
    public VideoNotFoundException(String message) {
        super(message);
    }
    public VideoNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

}
