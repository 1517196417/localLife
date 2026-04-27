# 退出登录功能实现说明

## 📋 功能概述

实现了基于 **Token + Redis** 的退出登录功能，用户点击"退出登录"按钮后，系统会删除Redis中存储的登录Token，实现安全退出。

---

## 🔧 实现方案

### 技术架构

```
前端（Vue.js）          后端（Spring Boot）        数据存储（Redis）
     │                        │                        │
     │  1. 点击退出登录        │                        │
     ├───────────────────────>│                        │
     │                        │                        │
     │  2. POST /user/logout  │                        │
     │  Header: authorization │                        │
     ├───────────────────────>│                        │
     │                        │  3. 删除Redis中的token  │
     │                        ├───────────────────────>│
     │                        │   DELETE login:token:xxx│
     │                        │                        │
     │                        │  4. 返回成功            │
     │<───────────────────────┤                        │
     │                        │                        │
     │  5. 清理sessionStorage │                        │
     │  6. 跳转到首页         │                        │
```

---

## 📝 修改的文件

### 1. 后端接口层

**文件**: `src/main/java/com/hmdp/service/IUserService.java`

**修改内容**:
```java
/**
 * 退出登录
 * @param token 用户登录Token
 * @return 操作结果
 */
Result logout(String token);
```

---

### 2. 后端实现层

**文件**: `src/main/java/com/hmdp/service/impl/UserServiceImpl.java`

**修改内容**:
```java
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
```

**额外修改**: 添加 `@Slf4j` 注解支持日志记录

---

### 3. 后端控制器层

**文件**: `src/main/java/com/hmdp/controller/UserController.java`

**修改内容**:
```java
/**
 * 登出功能
 * @return 无
 */
@PostMapping("/logout")
public Result logout(@RequestHeader(value = "authorization", required = false) String token){
    // 实现退出登录功能：删除Redis中的token
    return userService.logout(token);
}
```

**关键点**:
- 使用 `@RequestHeader` 从请求头获取 `authorization`
- `required = false` 允许token为空（避免400错误）

---

### 4. 前端页面（已存在，无需修改）

**文件**: `hmdp/info.html`

**现有代码**（第187-196行）:
```javascript
logout() {
  axios.post("/user/logout")
    .then(() => {
      // 清理session
      sessionStorage.removeItem("token")
      // 跳转
      location.href = "/"
    })
    .catch(this.$message.error)
}
```

**工作流程**:
1. 调用后端 `/user/logout` 接口
2. axios拦截器自动在请求头添加 `authorization: token`
3. 后端删除Redis中的token
4. 前端清理 `sessionStorage` 中的token
5. 跳转到首页

---

## 🧪 测试步骤

### 前置条件

1. 确保Redis服务已启动
2. 确保后端服务已启动（`mvn spring-boot:run`）
3. 确保前端可以访问（通过浏览器打开 `index.html`）

---

### 测试流程

#### 步骤1: 登录系统

1. 打开浏览器访问 `http://localhost:8080/login.html`
2. 输入手机号（如：`13800138000`）
3. 点击"获取验证码"
4. 查看后端日志获取验证码（如：`123456`）
5. 输入验证码，点击"登录"
6. 登录成功后，会自动跳转到首页

**验证登录成功**:
```bash
# 在Redis中查看token
redis-cli
> KEYS login:token:*
# 应该能看到类似这样的key：
# login:token:a1b2c3d4-e5f6-7890-abcd-ef1234567890
```

---

#### 步骤2: 访问个人主页

1. 点击底部导航栏的"我的"按钮
2. 进入个人主页（`info.html`）
3. 页面应该显示用户信息（昵称、头像等）

**验证Token在请求头**:
- 打开浏览器开发者工具（F12）
- 切换到"Network"标签
- 查看 `/user/me` 请求
- 请求头中应该包含：`authorization: xxx-xxx-xxx`

---

#### 步骤3: 点击退出登录

1. 在个人主页右上角，点击"退出登录"按钮
2. 系统应该自动跳转到首页

**验证退出成功**:

1. **检查Redis**:
```bash
redis-cli
> KEYS login:token:*
# 应该返回空（或没有刚才的token）
```

2. **检查sessionStorage**:
- 打开浏览器开发者工具（F12）
- 切换到"Application"标签
- 展开"Session Storage"
- 应该没有 `token` 这个key

3. **尝试访问需要登录的页面**:
- 访问 `http://localhost:8080/info.html`
- 应该自动跳转到登录页面（因为401拦截）

---

### 预期结果

✅ **成功的表现**:
1. 点击"退出登录"后，立即跳转到首页
2. Redis中的 `login:token:xxx` key被删除
3. 前端 `sessionStorage` 中的 `token` 被清除
4. 再次访问个人主页，自动跳转到登录页
5. 后端日志显示：`用户退出登录成功，token: xxx`

---

## 🔍 常见问题排查

### 问题1: 点击退出登录后没有反应

**可能原因**:
- 后端服务未启动
- 前端请求被拦截

**排查方法**:
1. 打开浏览器开发者工具（F12）
2. 查看Console标签，是否有错误信息
3. 查看Network标签，`/user/logout` 请求的状态码

**解决方案**:
- 确保后端服务正常运行
- 检查 `common.js` 中的axios配置

---

### 问题2: 退出登录后，刷新页面仍然显示登录状态

**可能原因**:
- Redis中的token未被删除
- 前端未清理sessionStorage

**排查方法**:
```bash
# 检查Redis
redis-cli
> KEYS login:token:*
```

**解决方案**:
- 检查后端日志，确认logout方法是否被调用
- 检查Redis的 `LOGIN_USER_KEY` 常量是否正确

---

### 问题3: 后端报错 "token不能为空"

**可能原因**:
- 前端未正确传递token
- axios拦截器未工作

**排查方法**:
1. 查看Network标签中的 `/user/logout` 请求
2. 检查Request Headers是否包含 `authorization`

**解决方案**:
- 检查 `common.js` 中的axios拦截器配置
- 确保 `sessionStorage.getItem("token")` 有值

---

## 📊 Redis数据结构

### 登录Token存储

**Key格式**: `login:token:{token}`

**数据类型**: Hash

**示例**:
```
Key: login:token:a1b2c3d4-e5f6-7890-abcd-ef1234567890
Value: {
  "id": "1",
  "nickName": "虎哥",
  "icon": ""
}
TTL: 30分钟
```

### 退出登录操作

```bash
# 删除token
DEL login:token:a1b2c3d4-e5f6-7890-abcd-ef1234567890

# 返回: (integer) 1 表示删除成功
```

---

## 🔐 安全特性

### 1. Token唯一性
- 每次登录生成新的UUID作为token
- 格式：`a1b2c3d4-e5f6-7890-abcd-ef1234567890`

### 2. 自动过期
- Token默认30分钟过期
- 每次访问自动刷新TTL（滑动过期）

### 3. 安全删除
- 退出登录时彻底删除Redis中的token
- 即使token已不存在也返回成功（防止信息泄露）

### 4. 前端清理
- 清理sessionStorage中的token
- 跳转到首页，避免停留在需要登录的页面

---

## 📈 性能优化建议

### 1. 批量清理（未来优化）
如果用户多端登录，可以记录所有token，一次性清理：

```java
// 记录用户的所有token
Key: user:tokens:{userId}
Type: Set
Value: [token1, token2, token3]

// 退出登录时批量删除
SMEMBERS user:tokens:{userId}
→ 遍历删除所有login:token:{token}
→ 删除user:tokens:{userId}
```

### 2. 异步删除（未来优化）
使用异步删除，不阻塞主线程：

```java
@Async
public void asyncDeleteToken(String tokenKey) {
    stringRedisTemplate.delete(tokenKey);
}
```

---

## 🎯 功能演示

### 正常流程

```
用户操作流程：
1. 登录系统 ✅
2. 浏览商铺、发布笔记 ✅
3. 进入个人主页 ✅
4. 点击"退出登录" ✅
5. 自动跳转首页 ✅
6. 再次访问个人主页 → 自动跳转登录页 ✅
```

### 异常流程

```
场景1: Token已过期
→ Redis中无此token
→ 返回成功（不报错）
→ 前端正常跳转

场景2: 未登录直接访问个人主页
→ 拦截器返回401
→ 前端自动跳转登录页

场景3: 退出登录时网络异常
→ axios catch错误
→ 显示错误提示
→ 保留当前页面
```

---

## 📚 相关代码位置

| 模块 | 文件路径 | 说明 |
|------|---------|------|
| 接口定义 | `src/main/java/com/hmdp/service/IUserService.java` | logout方法声明 |
| 业务实现 | `src/main/java/com/hmdp/service/impl/UserServiceImpl.java` | logout方法实现 |
| 控制器 | `src/main/java/com/hmdp/controller/UserController.java` | /user/logout接口 |
| 拦截器 | `src/main/java/com/hmdp/utils/RefreshInterceptor.java` | Token刷新拦截器 |
| 拦截器 | `src/main/java/com/hmdp/utils/LoginInterceptor.java` | 登录验证拦截器 |
| 前端页面 | `hmdp/info.html` | 个人主页（含退出按钮） |
| 前端工具 | `hmdp/js/common.js` | axios拦截器配置 |
| Redis常量 | `src/main/java/com/hmdp/utils/RedisConstants.java` | LOGIN_USER_KEY定义 |

---

## ✅ 验收标准

- [x] 后端接口正常返回
- [x] Redis中的token被删除
- [x] 前端sessionStorage被清理
- [x] 退出后自动跳转首页
- [x] 再次访问需要登录的页面时，自动跳转登录页
- [x] 异常情况下不会崩溃（token为空、token不存在等）
- [x] 日志记录完整（成功、失败、警告）

---

**文档版本**: v1.0  
**创建日期**: 2026-04-24  
**维护者**: LocalLife技术团队
