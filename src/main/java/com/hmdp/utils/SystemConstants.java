package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Value;

public class SystemConstants {
    @Value("${app.upload-dir:/home/app/nginx/html/imgs}")
    public static String IMAGE_UPLOAD_DIR;

    public static final String USER_NICK_NAME_PREFIX = "user_";
    public static final int DEFAULT_PAGE_SIZE = 100;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int BLOG_PAGE_SIZE = 10;
}
