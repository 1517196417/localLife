# LocalLife 项目部署文档

> 部署目标：云服务器 `120.55.195.171`（Alibaba Cloud Linux 3.2104LTS）
>
> 技术栈：Spring Boot 2.3.12 + MySQL 5.7 + Redis 6.2 + RabbitMQ 3.8 + Elasticsearch 7.12

---

## 目录

- [1. 部署架构](#1-部署架构)
- [2. 基础设施准备（Docker Compose）](#2-基础设施准备docker-compose)
- [3. 数据库初始化](#3-数据库初始化)
- [4. 后端部署](#4-后端部署)
- [5. 前端部署与Nginx配置](#5-前端部署与nginx配置)
- [6. 验证部署](#6-验证部署)
- [7. 日常运维](#7-日常运维)
- [8. 附录：pom.xml 配置说明](#8-附录pomxml-配置说明)

---

## 1. 部署架构

```
用户浏览器 (http://120.55.195.171)
       │
       ▼
┌──────────────────┐     /api/*     ┌──────────────────────┐
│   Nginx (80端口)   │──────────────►│  Spring Boot (8081端口) │
│  前端静态文件代理   │               │   Docker容器           │
└──────────────────┘               └──────────┬───────────┘
                                               │
                         ┌─────────────────────┼─────────────────────┐
                         ▼                     ▼                     ▼
                  ┌────────────┐       ┌────────────┐       ┌──────────────┐
                  │   MySQL    │       │   Redis    │       │   RabbitMQ   │
                  │   3306端口  │       │   6379端口  │       │   5672端口    │
                  └────────────┘       └────────────┘       └──────────────┘
                                                              ┌──────────────┐
                                                              │Elasticsearch │
                                                              │   9200端口    │
                                                              └──────────────┘
```

### 关键配置总结

| 配置项 | 值 | 说明 |
|--------|------|------|
| 后端端口 | 8081 | Spring Boot 服务端口 |
| 前端API路径 | `/api` | 通过 Nginx 代理到后端 |
| 激活Profile | `docker` | 使用 `application-docker.yaml` 配置 |
| Docker网络 | `locallife` | 所有容器在同一网络，通过服务名通信 |

---

## 2. 基础设施准备（Docker Compose）

### 2.1 SSH登录服务器

```bash
ssh root@120.55.195.171
```

### 2.2 检查Docker状态

```bash
docker info
docker compose version   # 检查 Docker Compose 是否安装
```

> 如果未安装 Docker Compose，请先安装：
> ```bash
> curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
> chmod +x /usr/local/bin/docker-compose
> ```

### 2.3 创建项目目录

```bash
mkdir -p /home/app/locallife
mkdir -p /home/app/nginx/html
mkdir -p /home/app/hm-dianping/logs
```

### 2.4 创建基础设施 Docker Compose 文件

在服务器上创建 `/home/app/locallife/docker-compose-infra.yml`：

```yaml
version: '3.8'
services:
  mysql:
    image: mysql:5.7
    container_name: mysql
    environment:
      MYSQL_ROOT_PASSWORD: root123456
      MYSQL_DATABASE: hmdp
    ports:
      - "3306:3306"
    volumes:
      - /home/app/mysql/data:/var/lib/mysql
      - /home/app/mysql/init:/docker-entrypoint-initdb.d
    networks:
      - locallife
    command: --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci

  redis:
    image: redis:6.2
    container_name: redis
    command: redis-server --requirepass redis123456
    ports:
      - "6379:6379"
    networks:
      - locallife

  rabbitmq:
    image: rabbitmq:3.8-management
    container_name: rabbitmq
    environment:
      RABBITMQ_DEFAULT_USER: guest
      RABBITMQ_DEFAULT_PASS: guest
    ports:
      - "5672:5672"
      - "15672:15672"
    networks:
      - locallife

  elasticsearch:
    image: elasticsearch:7.12.1
    container_name: elasticsearch
    environment:
      - discovery.type=single-node
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
    ports:
      - "9200:9200"
    volumes:
      - /home/app/elasticsearch/data:/usr/share/elasticsearch/data
    networks:
      - locallife

networks:
  locallife:
    driver: bridge
```

> ⚠️ **注意**：Elasticsearch 7.12.1 可能存在安全漏洞，生产环境建议使用 7.17.x 或 8.x 版本，并配置 `xpack.security.enabled: true`。

### 2.5 启动基础设施

```bash
cd /home/app/locallife
docker compose -f docker-compose-infra.yml up -d

# 检查所有服务是否正常启动
docker ps

# 查看启动日志（如有问题）
docker compose -f docker-compose-infra.yml logs
```

---

## 3. 数据库初始化

### 3.1 导出本地数据库

在本地开发机用 MySQL 客户端（如 Navicat、DBeaver、HeidiSQL 等）导出 `hmdp` 数据库为 `.sql` 文件。

或者用命令行导出：

```bash
mysqldump -uroot -p hmdp > hmdp.sql
```

### 3.2 将 SQL 文件传给服务器

**推荐使用图形化工具上传**，例如：

- **WinSCP**（免费）：像 FTP 一样连接服务器 `120.55.195.171`，用户名 `root`，密码就是你 SSH 登录的密码（或使用密钥），然后直接把文件拖拽到 `/home/app/locallife/` 目录
- **FinalShell**：国内常用的服务器管理工具，支持文件拖拽上传
- **宝塔面板的文件管理器**：如果服务器装了宝塔
- **IDEA 内置工具**：Tools → Deployment → Upload

> 不需要用命令行的 `scp`，拖拽更直观。上传到服务器的 `/home/app/locallife/` 目录即可。

### 3.3 导入到服务器数据库

```bash
# SSH 登录服务器
ssh root@120.55.195.171

# 把 SQL 文件导入 Docker 中的 MySQL
docker exec -i mysql mysql -uroot -proot123456 hmdp < /home/app/locallife/hmdp.sql
```

### 3.3 验证数据库

```bash
docker exec -i mysql mysql -uroot -proot123456 hmdp -e "SHOW TABLES;"
```

---

## 4. 后端部署

后端部署提供两种方案，推荐使用 **方案A**（服务器上直接构建，最简单）。

### 4.1 方案A：服务器上直接构建（推荐，最简单）

在服务器上安装 Git 和 Maven，直接拉取代码构建：

```bash
# 在服务器上安装 Git 和 Maven
ssh root@120.55.195.171
yum install -y git maven

# 克隆/更新代码
cd /home/app/locallife
git clone https://github.com/1517196417/localLife.git . 2>/dev/null || git pull

# 构建项目（跳过测试）
mvn clean package -DskipTests

# 构建 Docker 镜像
docker build -t locallife/hm-dianping:0.0.1-SNAPSHOT .

# 运行容器（加入 locallife 网络，使用 docker 配置）
docker run -d \
  --name hm-dianping \
  --network locallife \
  -p 8081:8081 \
  -e JAVA_OPTS="-Xms256m -Xmx256m" \
  -e SPRING_ARGS="--spring.profiles.active=docker" \
  -v /home/app/hm-dianping/logs:/tmp/logs \
  --restart unless-stopped \
  locallife/hm-dianping:0.0.1-SNAPSHOT

# 查看启动日志
docker logs -f hm-dianping
```

### 4.2 方案B：本地构建，上传 JAR 到服务器部署（无需服务器安装 Maven/Git）

如果服务器上不想安装 Maven 和 Git，可以在本地开发机构建好 JAR 包，上传到服务器：

```bash
# === 本地开发机操作 ===

# 1. 本地构建 JAR（跳过测试）
cd D:/javacode/github_code/localLife/localLife
mvn clean package -DskipTests

# 2. 将 JAR 包和 Dockerfile 上传到服务器
scp target/hm-dianping-0.0.1-SNAPSHOT.jar root@120.55.195.171:/home/app/locallife/
scp Dockerfile root@120.55.195.171:/home/app/locallife/

# === 服务器操作 ===
ssh root@120.55.195.171
cd /home/app/locallife

# 3. 在服务器上构建 Docker 镜像
docker build -t locallife/hm-dianping:0.0.1-SNAPSHOT .

# 4. 运行容器
docker run -d \
  --name hm-dianping \
  --network locallife \
  -p 8081:8081 \
  -e JAVA_OPTS="-Xms256m -Xmx256m" \
  -e SPRING_ARGS="--spring.profiles.active=docker" \
  -v /home/app/hm-dianping/logs:/tmp/logs \
  --restart unless-stopped \
  locallife/hm-dianping:0.0.1-SNAPSHOT

# 查看启动日志
docker logs -f hm-dianping
```

### 4.4 修改图片上传路径

在部署前，需要修改 `SystemConstants.java` 中的图片上传路径，**否则上传功能会报错**：

```bash
# 在服务器上创建图片上传目录（与后端容器映射一致）
# 需要修改 SystemConstants.IMAGE_UPLOAD_DIR 为服务器路径
# 或者在 application-docker.yaml 中增加配置覆盖
```

建议将图片存储在 Nginx 的静态目录下，并新增一个配置类从 `application-docker.yaml` 读取：

```yaml
# 在 application-docker.yaml 中添加
app:
  upload-dir: /home/app/nginx/html/imgs
```

然后在代码中通过 `@Value("${app.upload-dir}")` 注入，替代硬编码路径。

---

## 5. 前端部署与Nginx配置

### 5.1 上传前端文件到服务器

**同样推荐使用图形化工具上传**（WinSCP、FinalShell、宝塔等）：

- 本地目录：项目中的 `hmdp/` 文件夹
- 服务器目标目录：`/home/app/nginx/html/`
- 直接选中 `hmdp/` 下的所有文件，拖拽上传即可

> 如果后续更新了前端文件，重新上传覆盖到 `/home/app/nginx/html/` 即可。

### 5.2 安装并配置 Nginx

```bash
# 在服务器上安装 Nginx
ssh root@120.55.195.171
yum install -y nginx
```

创建 Nginx 配置文件 `/etc/nginx/conf.d/locallife.conf`：

```nginx
server {
    listen       80;
    server_name  120.55.195.171;

    # 前端静态文件根目录
    location / {
        root /home/app/nginx/html;
        index index.html index.htm;
        # SPA路由支持
        try_files $uri $uri/ /index.html;
    }

    # 后端 API 代理
    location /api {
        proxy_pass http://127.0.0.1:8081;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # WebSocket 支持（如果需要聊天功能）
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }

    # 后端直接路径代理（如果前端使用 /user、/shop 等路径而不是 /api/user）
    location ~ ^/(user|shop|blog|voucher|upload|follow|message|seckill) {
        proxy_pass http://127.0.0.1:8081;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # 图片访问
    location /imgs {
        root /home/app/nginx/html;
        expires 7d;
        add_header Cache-Control "public, immutable";
    }

    # 静态文件缓存
    location ~* \.(jpg|jpeg|png|gif|ico|css|js|svg|woff|woff2|ttf)$ {
        root /home/app/nginx/html;
        expires 7d;
        add_header Cache-Control "public, immutable";
    }

    # 错误页面
    error_page   500 502 503 504  /50x.html;
    location = /50x.html {
        root   /usr/share/nginx/html;
    }
}
```

### 5.3 测试并重启 Nginx

```bash
nginx -t                    # 测试配置是否正确
systemctl restart nginx     # 重启 Nginx
systemctl enable nginx      # 设置开机自启
```

### 5.4 验证前端

```bash
curl -I http://localhost
```

应该返回 `200 OK` 并显示 HTML 内容。

---

## 6. 验证部署

### 6.1 检查所有服务

```bash
# 列出所有运行中的容器
docker ps

# 应该看到以下容器正常运行：
# - mysql
# - redis
# - rabbitmq
# - elasticsearch
# - hm-dianping
```

### 6.2 验证后端 API

```bash
# 从服务器本地测试
curl http://127.0.0.1:8081/api/shop-type/list

# 或通过浏览器访问
# http://120.55.195.171/api/shop-type/list
```

### 6.3 验证前端页面

浏览器访问：`http://120.55.195.171`

### 6.4 查看后端日志（如遇问题）

```bash
docker logs -f hm-dianping           # 实时日志
docker logs --tail 100 hm-dianping   # 最近100行日志
```

---

## 7. 日常运维

### 7.1 重启服务

```bash
# 重启后端
docker restart hm-dianping

# 重启基础设施
cd /home/app/locallife
docker compose -f docker-compose-infra.yml restart

# 重启 Nginx
systemctl restart nginx
```

### 7.2 更新后端

```bash
# 方案A（推荐）：服务器上重新构建
cd /home/app/locallife
git pull
mvn clean package -DskipTests
docker stop hm-dianping && docker rm hm-dianping
docker build -t locallife/hm-dianping:0.0.1-SNAPSHOT .
docker run -d --name hm-dianping --network locallife -p 8081:8081 \
  -e SPRING_ARGS="--spring.profiles.active=docker" \
  -v /home/app/hm-dianping/logs:/tmp/logs \
  --restart unless-stopped \
  locallife/hm-dianping:0.0.1-SNAPSHOT

# 方案B：本地构建 JAR 上传到服务器
# 本地执行：
mvn clean package -DskipTests
scp target/hm-dianping-0.0.1-SNAPSHOT.jar root@120.55.195.171:/home/app/locallife/
# 服务器执行：
docker stop hm-dianping && docker rm hm-dianping
docker build -t locallife/hm-dianping:0.0.1-SNAPSHOT .
docker run -d --name hm-dianping --network locallife -p 8081:8081 \
  -e SPRING_ARGS="--spring.profiles.active=docker" \
  -v /home/app/hm-dianping/logs:/tmp/logs \
  --restart unless-stopped \
  locallife/hm-dianping:0.0.1-SNAPSHOT
```

### 7.3 更新前端

用图形化工具（WinSCP、FinalShell 等）把 `hmdp/` 下的文件上传到服务器的 `/home/app/nginx/html/` 覆盖即可。

### 7.4 数据库备份

```bash
# 定时备份（建议加入 crontab）
docker exec mysql mysqldump -uroot -proot123456 hmdp > /home/app/backup/hmdp_$(date +%Y%m%d).sql
```

### 7.5 日志查看

```bash
# 后端日志
tail -f /home/app/hm-dianping/logs/spring-boot.log

# Nginx 日志
tail -f /var/log/nginx/access.log
tail -f /var/log/nginx/error.log
```

---

## 8. 附录：pom.xml 配置说明

### 8.1 fabric8 Docker 插件配置

```xml
<!-- pom.xml 中相关配置（已有，按需修改） -->
<properties>
    <docker.image.prefix>locallife</docker.image.prefix>
    <docker.host>120.55.195.171:2376</docker.host>
    <docker.cert.path>/home/docker-ca</docker.cert.path>
    <docker.jvm.opts>-Xms256m -Xmx256m</docker.jvm.opts>
    <docker.spring.args>--spring.profiles.active=docker</docker.spring.args>
</properties>
```

| 属性 | 说明 | 建议值 |
|------|------|--------|
| `docker.host` | Docker 远程地址 | 本地构建时改为 `unix:///var/run/docker.sock` |
| `docker.cert.path` | TLS 证书路径 | 如不用 TLS 可注释掉 certPath |
| `docker.jvm.opts` | JVM 参数 | 根据服务器内存调整 |
| `docker.spring.args` | Spring 启动参数 | 保持 `--spring.profiles.active=docker` |

### 8.2 配置说明对比

| 环境 | 配置文件 | MySQL地址 | Redis地址 | 用途 |
|------|---------|-----------|-----------|------|
| 本地开发 | `application.yaml` | `127.0.0.1:3306` | `192.168.88.130` | 本地开发调试 |
| Docker部署 | `application-docker.yaml` | `mysql:3306` | `redis:6379` | 容器化部署 |

> `application-docker.yaml` 中使用的是 Docker 服务名（如 `mysql`、`redis`），因为所有容器在同一个 `locallife` 网络中，Docker 内置 DNS 会自动解析服务名为容器 IP。

### 8.3 需要修改的代码

| 文件 | 内容 | 部署前操作 |
|------|------|-----------|
| `src/main/java/com/hmdp/utils/SystemConstants.java` | `IMAGE_UPLOAD_DIR` 硬编码为本地路径 | 需改为服务器上的路径，如 `/home/app/nginx/html/imgs` |

建议将 `IMAGE_UPLOAD_DIR` 改为从配置文件读取，避免硬编码：

```java
// SystemConstants.java 修改为
@Value("${app.upload-dir:/home/app/nginx/html/imgs}")
public static String IMAGE_UPLOAD_DIR;
```

并在 `application-docker.yaml` 中添加：
```yaml
app:
  upload-dir: /home/app/nginx/html/imgs
```

---

## 快速部署 Cheat Sheet

```bash
# === 一键部署流程（SSH到服务器后） ===

# 1. 启动基础设施
cd /home/app/locallife
docker compose -f docker-compose-infra.yml up -d

# 2. 导入数据库
docker exec -i mysql mysql -uroot -proot123456 hmdp < hmdp.sql

# 3. 构建并运行后端（方案A）
cd /home/app/locallife
git pull
mvn clean package -DskipTests
docker build -t locallife/hm-dianping:0.0.1-SNAPSHOT .
docker run -d --name hm-dianping --network locallife -p 8081:8081 \
  -e SPRING_ARGS="--spring.profiles.active=docker" \
  -v /home/app/hm-dianping/logs:/tmp/logs \
  --restart unless-stopped \
  locallife/hm-dianping:0.0.1-SNAPSHOT

# 4. 配置 Nginx 并上传前端
yum install -y nginx
# 创建 /etc/nginx/conf.d/locallife.conf（见上文）
nginx -t && systemctl restart nginx
# 上传前端文件到 /home/app/nginx/html/

# 5. 验证
curl http://127.0.0.1/api/shop-type/list
# 浏览器访问 http://120.55.195.171
```

---

> **文档版本**：v1.0
> **最后更新**：2026-04-28
> **如有问题**：检查 `docker logs hm-dianping` 和 `/var/log/nginx/error.log`