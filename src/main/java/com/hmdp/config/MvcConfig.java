package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshInterceptor;
import com.hmdp.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.filter.HiddenHttpMethodFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    
    /**
     * 配置HiddenHttpMethodFilter，支持PUT和DELETE请求
     */
    @org.springframework.context.annotation.Bean
    public HiddenHttpMethodFilter hiddenHttpMethodFilter() {
        return new HiddenHttpMethodFilter();
    }
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RefreshInterceptor(stringRedisTemplate))
                .addPathPatterns("/**").order(0);

        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns("/user/code",
                                    "/user/login",
                                    "/blog/hot",
                                    "/blog/{id}",  // 允许游客查看博客详情（分享链接）
                                    "/shop/**",
                                    "/shop-type/**",
                                    "/upload/**",
                                    "/imgs/**",
                                    "/voucher/**",
                                    "/blog-comments/**",
                                    "/shop-comments/**"

                ).order(1)    ;
    }
    
    /**
     * 添加静态资源映射
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 配置图片访问路径映射 - 使用绝对路径
        String imageUploadDir = SystemConstants.IMAGE_UPLOAD_DIR;
        // 确保路径以 / 结尾
        if (!imageUploadDir.endsWith("/")) {
            imageUploadDir = imageUploadDir + "/";
        }
        
        // 外部图片目录映射到 /imgs/** 路径
        registry.addResourceHandler("/imgs/**")
                .addResourceLocations("file:" + imageUploadDir);
    }
}
