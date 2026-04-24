# Nginx配置与使用指南 - LocalLife

## 📋 概述

本指南帮助你使用Nginx代理前端静态文件和后端API，实现前后端统一访问，避免跨域问题。

---

## 🏗️ 架构说明

```
浏览器
  ↓
  ├─ 访问: http://localhost/
  │   └─ Nginx返回前端静态文件（HTML/CSS/JS）
  │
  └─ 请求: http://localhost/api/user/login
      └─ Nginx代理到: http://localhost:8080/user/login
          └─ Spring Boot后端处理请求
```

**优势**：
- ✅ 统一域名，无跨域问题
- ✅ 前端修改后立即生效（刷新即可）
- ✅ 支持gzip压缩，提升性能
- ✅ 静态文件缓存，加速访问

---

## 📦 一、安装Nginx

### Windows系统

#### 方式1：直接下载（推荐）

1. **下载Nginx**：
   - 官网：http://nginx.org/en/download.html
   - 下载Windows稳定版（如：nginx/Windows-1.24.0）

2. **解压到指定目录**：
   ```
   D:\nginx
   ```

3. **验证安装**：
   ```powershell
   cd D:\nginx
   .\nginx.exe -v
   # 应该显示：nginx version: nginx/1.24.0
   ```

#### 方式2：使用Chocolatey包管理器

```powershell
# 安装Chocolatey（如果未安装）
Set-ExecutionPolicy Bypass -Scope Process -Force; [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072; iex ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))

# 安装Nginx
choco install nginx
```

---

## ⚙️ 二、配置Nginx

### 步骤1：备份原配置

```powershell
cd D:\nginx\conf
copy nginx.conf nginx.conf.bak
```

### 步骤2：复制项目配置

将项目中的 `nginx.conf` 复制到Nginx配置目录：

```powershell
# 从项目目录执行
copy D:\javacode\github_code\localLife\localLife\nginx.conf D:\nginx\conf\nginx.conf
```

### 步骤3：检查配置

```powershell
cd D:\nginx
.\nginx.exe -t
```

**成功输出**：
```
nginx: the configuration file D:\nginx/conf/nginx.conf syntax is ok
nginx: configuration file D:\nginx/conf/nginx.conf test is successful
```

---

## 🚀 三、启动Nginx

### 启动服务

```powershell
cd D:\nginx
.\nginx.exe
```

### 验证启动

1. **打开浏览器访问**：
   ```
   http://localhost
   ```
   
2. **应该看到**：
   - LocalLife首页
   - 前端静态文件正常加载

### 常用命令

```powershell
# 启动
.\nginx.exe

# 停止
.\nginx.exe -s stop

# 优雅停止（处理完当前请求）
.\nginx.exe -s quit

# 重新加载配置（不中断服务）
.\nginx.exe -s reload

# 测试配置文件
.\nginx.exe -t
```

---

## 🔧 四、完整启动流程

### 步骤1：启动后端服务

```powershell
cd D:\javacode\github_code\localLife\localLife
mvn spring-boot:run
```

**验证**：
- 后端启动在 `http://localhost:8080`
- 访问 `http://localhost:8080/user/me` 应该返回401（未登录）

### 步骤2：启动Nginx

```powershell
cd D:\nginx
.\nginx.exe
```

**验证**：
- 访问 `http://localhost` 应该显示前端首页

### 步骤3：测试前后端联通

1. **访问前端**：
   ```
   http://localhost/index.html
   ```

2. **登录测试**：
   - 访问 `http://localhost/login.html`
   - 输入手机号和验证码
   - 登录成功后，查看Network标签
   - 请求应该是：`http://localhost/api/user/login`

3. **退出登录测试**：
   - 访问个人主页
   - 点击"退出登录"
   - 应该正常工作

---

## 📊 五、请求流程详解

### 前端请求示例

**请求**：
```
http://localhost/api/user/logout
```

**Nginx处理**：
```nginx
location /api {
    proxy_pass http://localhost:8080;
}
```

**实际转发到后端**：
```
http://localhost:8080/user/logout
```

**注意**：`/api` 前缀会被自动去掉！

---

## 🔍 六、前端配置说明

### common.js 配置

文件：`hmdp/js/common.js`

```javascript
// 智能判断：file://协议用完整URL，http://协议用相对路径
let commonURL = window.location.protocol === 'file:' 
  ? "http://localhost:8080"   // 直接打开时使用
  : "/api";                   // 通过Nginx访问时使用
```

**工作原理**：
- 通过 `http://localhost` 访问 → 使用 `/api`
- Nginx代理 `/api` → `http://localhost:8080`
- 完美解决跨域问题！

---

## ❗ 七、常见问题排查

### 问题1：Nginx启动失败

**错误**：
```
nginx: [emerg] bind() to 0.0.0.0:80 failed (10013: ...)
```

**原因**：80端口被占用

**解决方案**：

方案A：关闭占用80端口的程序
```powershell
# 查看占用80端口的进程
netstat -ano | findstr :80

# 结束进程（替换PID）
taskkill /PID <PID> /F
```

方案B：修改Nginx监听端口
```nginx
# nginx.conf
server {
    listen 8081;  # 改为8081端口
    ...
}
```

然后访问：`http://localhost:8081`

---

### 问题2：前端能访问，但API请求404

**原因**：Nginx配置未生效

**排查步骤**：

1. **检查Nginx配置**：
   ```powershell
   .\nginx.exe -t
   ```

2. **重新加载配置**：
   ```powershell
   .\nginx.exe -s reload
   ```

3. **查看Nginx日志**：
   ```powershell
   type D:\nginx\logs\access.log
   type D:\nginx\logs\error.log
   ```

4. **测试后端是否正常**：
   ```
   http://localhost:8080/user/me
   ```

---

### 问题3：API请求502 Bad Gateway

**原因**：后端服务未启动

**解决方案**：
```powershell
# 启动后端
cd D:\javacode\github_code\localLife\localLife
mvn spring-boot:run
```

---

### 问题4：跨域错误（CORS）

**不应该出现**，因为Nginx已经代理了。

**如果仍有跨域**：
1. 检查请求URL是否是 `http://localhost/api/...`
2. 检查Nginx配置是否正确
3. 清除浏览器缓存

---

## 📝 八、配置优化建议

### 1. 开启gzip压缩

已配置，可进一步压缩：
```nginx
gzip on;
gzip_comp_level 5;  # 压缩级别1-9，推荐5
gzip_min_length 256;  # 最小压缩大小
gzip_types 
    text/plain 
    text/css 
    application/json 
    application/javascript 
    text/xml 
    application/xml;
```

### 2. 静态文件缓存

已配置，图片缓存7天：
```nginx
location ~* \.(jpg|jpeg|png|gif|ico)$ {
    expires 7d;
    add_header Cache-Control "public, immutable";
}
```

### 3. 负载均衡（多后端实例）

```nginx
upstream backend {
    server localhost:8080;
    server localhost:8081;
    server localhost:8082;
}

location /api {
    proxy_pass http://backend;
}
```

### 4. HTTPS配置

```nginx
server {
    listen 443 ssl;
    server_name localhost;
    
    ssl_certificate     cert.pem;
    ssl_certificate_key cert.key;
    
    location / {
        root D:/javacode/github_code/localLife/localLife/hmdp;
    }
    
    location /api {
        proxy_pass http://localhost:8080;
    }
}
```

---

## 🧪 九、测试清单

### 基础功能测试

- [ ] 访问 `http://localhost` 能看到首页
- [ ] 访问 `http://localhost/login.html` 能登录
- [ ] 登录后能访问个人主页
- [ ] 点击"退出登录"能正常退出
- [ ] 退出后再次访问个人主页，跳转到登录页

### 网络请求测试

打开浏览器开发者工具（F12）：

- [ ] Network标签中，请求都是 `http://localhost/api/...`
- [ ] 没有跨域错误（CORS）
- [ ] 静态文件（CSS/JS/图片）返回200
- [ ] API请求返回正常数据

### 性能测试

- [ ] 页面加载时间 < 2秒
- [ ] 静态文件有缓存头（Cache-Control）
- [ ] 响应有gzip压缩（Content-Encoding: gzip）

---

## 📚 十、Nginx常用命令速查

```powershell
# 启动
.\nginx.exe

# 停止
.\nginx.exe -s stop

# 优雅停止
.\nginx.exe -s quit

# 重新加载配置
.\nginx.exe -s reload

# 测试配置
.\nginx.exe -t

# 查看版本
.\nginx.exe -v

# 查看详细信息
.\nginx.exe -V

# 查看帮助
.\nginx.exe -h
```

---

## 🔗 十一、相关文件位置

| 文件 | 路径 | 说明 |
|------|------|------|
| Nginx配置 | `D:\nginx\conf\nginx.conf` | Nginx主配置文件 |
| 项目配置 | `D:\javacode\...\localLife\nginx.conf` | 项目提供的配置模板 |
| 访问日志 | `D:\nginx\logs\access.log` | 请求日志 |
| 错误日志 | `D:\nginx\logs\error.log` | 错误日志 |
| 前端文件 | `D:\javacode\...\localLife\hmdp\` | 前端静态文件 |
| 后端服务 | `http://localhost:8080` | Spring Boot后端 |

---

## 🎯 十二、推荐工作流

### 日常开发

```
1. 启动后端
   └─ mvn spring-boot:run

2. 启动Nginx
   └─ D:\nginx\nginx.exe

3. 访问前端
   └─ http://localhost

4. 修改前端文件
   └─ 保存后刷新浏览器

5. 修改后端代码
   └─ 重启后端服务
```

### 调试技巧

1. **查看Nginx日志**：
   ```powershell
   tail -f D:\nginx\logs\access.log
   ```

2. **查看后端日志**：
   - 在IDEA或控制台查看Spring Boot日志

3. **浏览器开发者工具**：
   - Network标签：查看请求详情
   - Console标签：查看JS错误
   - Application标签：查看Storage

---

## ✅ 总结

使用Nginx代理后：

✅ **无跨域问题**：前后端统一域名  
✅ **修改即生效**：刷新浏览器即可  
✅ **性能优化**：gzip压缩 + 静态缓存  
✅ **易于部署**：配置简单，维护方便  

---

**文档版本**：v1.0  
**创建日期**：2026-04-24  
**维护者**：LocalLife技术团队
