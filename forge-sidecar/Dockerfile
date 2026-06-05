# Stage 1: Maven build
# Copy all module pom.xml files first so Maven can resolve the reactor
# without needing source for modules we don't build. Then copy only the
# source for forge-sidecar and its three dependencies.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Root pom and all module poms (reactor resolution — no source needed for unbuilt modules)
COPY pom.xml .
COPY forge-core/pom.xml           forge-core/pom.xml
COPY forge-game/pom.xml           forge-game/pom.xml
COPY forge-ai/pom.xml             forge-ai/pom.xml
COPY forge-sidecar/pom.xml        forge-sidecar/pom.xml
COPY forge-gui/pom.xml            forge-gui/pom.xml
COPY forge-gui-mobile/pom.xml     forge-gui-mobile/pom.xml
COPY forge-gui-mobile-dev/pom.xml forge-gui-mobile-dev/pom.xml
COPY forge-gui-desktop/pom.xml    forge-gui-desktop/pom.xml
COPY forge-gui-ios/pom.xml        forge-gui-ios/pom.xml
COPY forge-gui-android/pom.xml    forge-gui-android/pom.xml
COPY forge-lda/pom.xml            forge-lda/pom.xml
COPY adventure-editor/pom.xml     adventure-editor/pom.xml
COPY forge-installer/pom.xml      forge-installer/pom.xml

# Pre-fetch dependencies into the layer cache
RUN mvn dependency:go-offline -pl forge-sidecar -am -q --no-transfer-progress 2>/dev/null || true

# Copy sources only for the modules we actually compile
COPY forge-core/src     forge-core/src
COPY forge-game/src     forge-game/src
COPY forge-ai/src       forge-ai/src
COPY forge-sidecar/src  forge-sidecar/src

RUN mvn -pl forge-sidecar -am package -DskipTests -Dcheckstyle.skip=true -q --no-transfer-progress

# Stage 2: Runtime image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=build /build/forge-sidecar/target/forge-sidecar-2.0.13-SNAPSHOT.jar app.jar

# Bundle the Forge data directory (card scripts, editions, type lists).
# This is large (~285MB uncompressed) but keeps the container self-contained.
COPY forge-gui/res /app/res

EXPOSE 7070

# Override the hardcoded dev path in application.properties.
# Spring Boot relaxed binding: FORGE_RES_PATH → forge.res.path
ENV FORGE_RES_PATH=/app/res

ENTRYPOINT ["java", "-jar", "app.jar"]
