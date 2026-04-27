package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;
import static java.time.LocalTime.now;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1:检验手机号是否正确：不正确则返回报错信息
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机格式错误");
        }
        //2:手机号格式正确就发送验证码：调用第三方工具类
        String code = RandomUtil.randomNumbers(6);
        //保存验证码到redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //发送验证码(log日志模拟发送)
        log.debug("\n验证码: " + code);
        return Result.ok();
    }

    @Override
    public Result loginByForm(LoginFormDTO loginFormDTO, HttpSession session) {
        // 1：校验手机号格式
        String phone = loginFormDTO.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机格式错误");
        }
        // 2：校验验证码
        String code = loginFormDTO.getCode();
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误");
        }

//        // 3. 验证码校验通过，删除Redis中的验证码（防止重复使用）
        stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);

        // 4：查询/注册用户
        User user = query().eq("phone", phone).one();
        if (user == null) {
            user = createUserWithLogin(phone);
        }

        // 5：生成token作为登录标识
        String token = UUID.randomUUID().toString();
        String tokenKey = LOGIN_USER_KEY + token;

        try {
            // 6：对象转换，安全转换为Map，修复空指针问题
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            UserHolder.saveUser(userDTO);
//            Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
//                    CopyOptions.create()
//                            .setIgnoreNullValue(true)
//                            // 字段值处理：空值设为空字符串，非空值转String
//                            .setFieldValueEditor((fieldName, fieldValue) -> {
//                                if (fieldValue == null) {
//                                    return "";
//                                }
//                                return fieldValue.toString();
//                            })
//            );
            HashMap<String, String> userMap = new HashMap<>();
            userMap.put("nickName", userDTO.getNickName());
            userMap.put("id", userDTO.getId().toString());
            userMap.put("icon",  userDTO.getIcon());
            System.out.println("存入redis的userMap：" + userMap);

            // 7：存储用户信息到Redis Hash结构
            stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
            // 修复：使用正确的key设置过期时间
            stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

            // 8：返回token给前端
            return Result.ok(token);
        } catch (Exception e) {
            log.error("用户登录存储Redis异常", e);
            return Result.fail("登录失败，请稍后重试");
        }

    }



    private User createUserWithLogin(String phone){
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);

        return user;
    }

    @Override
    public Result logout(String token) {
        try {
            // 1. 校验token是否为空
            if (token == null || token.trim().isEmpty()) {
                return Result.fail("token不能为空");
            }
            
            // 2. 构建Redis中的token key
            String tokenKey = LOGIN_USER_KEY + token;
            
            // 3. 删除Redis中的用户登录信息
            Boolean deleted = stringRedisTemplate.delete(tokenKey);
            
            if (deleted != null && deleted) {
                log.info("用户退出登录成功，token: {}", token);
                return Result.ok();
            } else {
                log.warn("用户退出登录，token不存在: {}", token);
                return Result.ok(); // 即使token不存在也返回成功，避免前端错误
            }
        } catch (Exception e) {
            log.error("用户退出登录异常", e);
            return Result.fail("退出登录失败，请稍后重试");
        }
    }

    @Override
    public Result sign() {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.获取当前时间
        LocalDateTime now = LocalDateTime.now();
        String sign_suffix = now.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        String signKey = USER_SIGN_KEY + userId + sign_suffix;

        //3.获取当日是本月的第几天
        int dayOfMonth = now.getDayOfMonth();

        //4.执行setbit操作
        Boolean setBit = stringRedisTemplate.opsForValue().setBit(signKey, dayOfMonth - 1, true);
        //5.返回结果
        return Result.ok();
    }

    @Override
    public Result signCount() {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.获取当前时间
        LocalDateTime now = LocalDateTime.now();
        String sign_suffix = now.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        String signKey = USER_SIGN_KEY + userId + sign_suffix;

        //3.获取当日是本月的第几天
        int dayOfMonth = now.getDayOfMonth();

        //4.获取本月签到的二进制数据对应的十进制数num
        List<Long> results = stringRedisTemplate.opsForValue().bitField(signKey,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));

        if(results == null || results.isEmpty()) {
            return Result.ok(0);
        }
        Long num = results.get(0);
        if(num == null || num == 0L) {
            return Result.ok(0);
        }

        //5.循环倒序遍历num的二进制表示，统计其中的1的个数（通过与1与运算）
        int count = 0;
        while(true) {
            if ((num & 1) == 1) {
                count++;
            } else {
                break;
            }
            num >>>= 1; // 每次判断最后一位bit，然后右移一位
        }

        return Result.ok(count);
    }
    
    @Override
    public Result hasSignedToday() {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.获取当前时间
        LocalDateTime now = LocalDateTime.now();
        String signSuffix = now.format(DateTimeFormatter.ofPattern("yyyy:MM:"));
        String signKey = USER_SIGN_KEY + userId + signSuffix;
        
        //3.获取当日是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        
        //4.检查今天是否已签到
        Boolean signed = stringRedisTemplate.opsForValue().getBit(signKey, dayOfMonth - 1);
        
        return Result.ok(signed != null && signed);
    }

    @Override
    public Result updateUser(User user) {
        // 1. 获取当前登录用户ID
        Long userId = UserHolder.getUser().getId();
        user.setId(userId);
        
        // 2. 更新数据库
        updateById(user);
        
        // 3. 更新Redis缓存中的用户信息
        UserDTO userDTO = UserHolder.getUser();
        if (user.getNickName() != null) {
            userDTO.setNickName(user.getNickName());
        }
        if (user.getIcon() != null) {
            userDTO.setIcon(user.getIcon());
        }
        
        // 4. 同步更新Redis中所有该用户的token缓存（匹配 prefix + * 的keys）
        // 由于存在多个设备同时登录的情况，需要更新所有token
        try {
            Set<String> keys = stringRedisTemplate.keys(LOGIN_USER_KEY + "*");
            if (keys != null && !keys.isEmpty()) {
                for (String key : keys) {
                    // 检查这个key是否包含当前用户的ID
                    String nickName = (String) stringRedisTemplate.opsForHash().get(key, "nickName");
                    String idStr = (String) stringRedisTemplate.opsForHash().get(key, "id");
                    if (idStr != null && idStr.equals(userId.toString())) {
                        if (user.getNickName() != null) {
                            stringRedisTemplate.opsForHash().put(key, "nickName", user.getNickName());
                        }
                        if (user.getIcon() != null) {
                            stringRedisTemplate.opsForHash().put(key, "icon", user.getIcon());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("更新Redis缓存失败", e);
        }
        
        return Result.ok();
    }
}
