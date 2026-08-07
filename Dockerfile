# ---- build ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -q -B dependency:go-offline -DskipTests || true

COPY src ./src
RUN mvn -q -B -DskipTests package \
    && mv target/v2board-java-api-*.jar /app/app.jar

# ---- runtime ----
FROM eclipse-temurin:17-jre-jammy

# 构建期预装 sing-box，供第三方订阅源连通性探测使用
ARG SING_BOX_VERSION=1.13.16
ARG TARGETARCH
# 可选：自定义下载地址（默认 GitHub Release）
ARG SING_BOX_BASE_URL=https://github.com/SagerNet/sing-box/releases/download

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && ARCH="$(case "${TARGETARCH}" in amd64|x86_64) echo amd64 ;; arm64|aarch64) echo arm64 ;; *) echo amd64 ;; esac)" \
    && SING_BOX_URL="${SING_BOX_BASE_URL}/v${SING_BOX_VERSION}/sing-box-${SING_BOX_VERSION}-linux-${ARCH}.tar.gz" \
    && echo "Downloading sing-box from ${SING_BOX_URL}" \
    && curl -fsSL "${SING_BOX_URL}" -o /tmp/sing-box.tar.gz \
    && mkdir -p /tmp/sing-box \
    && tar -xzf /tmp/sing-box.tar.gz -C /tmp/sing-box --strip-components=1 \
    && install -m 0755 /tmp/sing-box/sing-box /usr/local/bin/sing-box \
    && rm -rf /tmp/sing-box /tmp/sing-box.tar.gz /var/lib/apt/lists/* \
    && /usr/local/bin/sing-box version

WORKDIR /app
COPY --from=build /app/app.jar /app/app.jar

ENV SING_BOX_PATH=/usr/local/bin/sing-box \
    PATH="/usr/local/bin:${PATH}" \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
