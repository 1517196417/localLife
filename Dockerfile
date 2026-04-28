# 基础镜像
FROM openjdk:8-jre-alpine

# 指定维护者
MAINTAINER locallife

# 设置时区
ENV TZ=Asia/Shanghai
RUN apk add --no-cache tzdata && \
    ln -sf /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone

# 将 target 目录下的 jar 包复制到容器中（fabric8 插件会自动处理）
ADD target/hm-dianping-0.0.1-SNAPSHOT.jar app.jar

# 定义 JVM 参数（可通过 docker-maven-plugin 的 <env> 覆盖）
ENV JAVA_OPTS="-Xms256m -Xmx256m"
ENV SPRING_ARGS=""
RUN echo "JAVA_OPTS=" $JAVA_OPTS

# 对外暴露端口
EXPOSE 8081

# 设置容器启动执行指令
CMD java $JAVA_OPTS -jar /app.jar --logging.file.path=/tmp/logs/spring-boot $SPRING_ARGS