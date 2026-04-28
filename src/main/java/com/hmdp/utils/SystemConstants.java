package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SystemConstants {
    public static String IMAGE_UPLOAD_DIR;

    @Value("${app.upload-dir:/home/app/nginx/html/imgs}")
    public void setImageUploadDir(String dir) {
        IMAGE_UPLOAD_DIR = dir;
    }

    public static final String USER_NICK_NAME_PREFIX = "user_";
    public static final int DEFAULT_PAGE_SIZE = 100;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int BLOG_PAGE_SIZE = 10;
}
