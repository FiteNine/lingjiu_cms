# 一条命令起完整 CMS：前端构建 -> 后端打包 -> 运行时镜像。
# 用法见 README「快速开始 · 方式二」；本地开发仍走 dev.cmd / dev.ps1。

# 1) 后台管理前端：产物输出到 src/main/resources/static（见 admin-ui/vite.config.ts 的 outDir）
FROM node:22-alpine AS ui
WORKDIR /build/admin-ui
COPY admin-ui/package.json admin-ui/package-lock.json ./
RUN npm ci
COPY admin-ui/ ./
RUN npm run build

# 2) 后端：打进上一步的前端产物，做成 Spring Boot 可执行 jar（测试交给 CI，不在镜像里跑）
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
# 只解析项目自身的依赖。不用 dependency:go-offline：它会把全部插件依赖也拉一遍
# （实测会跨仓库去追 google-cloud-policysimulator-bom 这类与项目无关的 BOM）
RUN mvn -B -DskipTests dependency:resolve
COPY src ./src
COPY --from=ui /build/src/main/resources/static ./src/main/resources/static
RUN mvn -B -DskipTests package

# 3) 运行时。不用 alpine：媒体处理走 ImageIO / Thumbnailator，alpine 的 JRE 缺 AWT 依赖。
FROM eclipse-temurin:21-jre
WORKDIR /app

# JVM 时区必须与数据库会话时区一致（application.yml 的 CMS_DB_TIMEZONE 默认 Asia/Shanghai），
# 否则「刚发布」的内容会被判成「还没到发布时间」而不进产物，且不报错。
ENV TZ=Asia/Shanghai

COPY --from=build /build/target/*.jar app.jar
# 站点目录随镜像带一份（含 demo 站点模板），首次挂载空数据卷时 Docker 会把它填进卷里
COPY sites ./sites
RUN mkdir -p uploads

# CMS_JWT_SECRET（≥32 字节）必须由运行时提供，否则启动即失败，见 application.yml 的 cms.jwt.secret
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
