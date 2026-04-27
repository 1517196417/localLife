# ==================== 构建阶段 ====================
FROM maven:3.6.3-openjdk-8 AS builder

WORKDIR /build

# 1. 复制 pom.xml 并下载依赖（利用 Docker 缓存层）
COPY pom.xml .
RUN mvn dependency:go-offline -B -DskipTests

# 2. 复制源代码并打包
COPY src ./src
RUN mvn package -B -DskipTests -Dmaven.test.skip=true

# ==================== 运行阶段 ====================
FROM openjdk:8-jre-slim

WORKDIR /app

# 时区
ENV TZ=Asia/Shanghai
RUN ln -sf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 从构建阶段复制打好的 JAR
COPY --from=builder /build/target/hm-dianping-0.0.1-SNAPSHOT.jar app.jar

# 暴露后端端口
EXPOSE 8081

# 启动命令（可通过环境变量覆盖配置）
ENTRYPOINT ["java", "-jar", "app.jar", \
    "--spring.profiles.active=${SPRING_PROFILE:-docker}", \
    "--server.port=8081"]