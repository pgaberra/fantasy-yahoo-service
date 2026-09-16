# ---- Build stage ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew

COPY src ./src
RUN ./gradlew bootJar --no-daemon

# Unpack the boot jar so the runtime loads classes from plain jars on the classpath. Run as a
# nested jar, class loading goes through a lock in Spring Boot's NestedJarFile, and on staging's
# two cores that deadlocked both virtual-thread carriers of the BFF, which runs the same way:
# it stopped answering everything, its health check included (2026-09-11).
RUN cp build/libs/*.jar app.jar \
    && java -Djarmode=tools -jar app.jar extract --destination extracted

# ---- Runtime stage ----
FROM eclipse-temurin:25-jre

# Coolify runs its health check with curl INSIDE the container, and the JRE image ships neither
# curl nor wget — so a container that started fine gets judged unhealthy and the deploy rolls
# back. Measured on 2026-08-09; see fantasy-workspace#2.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Run as an unprivileged user, not root, so a compromise of the app is not root in the container.
# A fixed UID/GID (10001) sits clear of the users the base image ships (Ubuntu's `ubuntu` is 1000)
# and is given numerically to USER, so a runtime can tell it is not root without reading
# /etc/passwd. The unpacked jars stay root-owned and read-only to it; the only place the app writes
# is java.io.tmpdir (/tmp, world-writable), where Tomcat keeps its work directory. The home
# directory is there for anything that caches under $HOME, such as Java preferences.
RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /home/app --create-home \
       --shell /usr/sbin/nologin app

WORKDIR /app
COPY --from=build /app/extracted/ ./

EXPOSE 8088
USER 10001:10001
ENTRYPOINT ["java", "-jar", "app.jar"]
