package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {
    Result sendCode(String phone, HttpSession session);

    Result loginByForm(LoginFormDTO loginFormDTO, HttpSession session);

    Result logout(String token);

    Result sign();

    Result signCount();
    
    /**
     * 检查今天是否已签到
     * @return 是否已签到
     */
    Result hasSignedToday();

    /**
     * 更新用户基本信息（昵称、头像）
     */
    Result updateUser(User user);
}
