FROM maven:3.8.7-eclipse-temurin-17 AS build
#COPY --from=mvnCache /root/.m2 /root/.m2
WORKDIR /build

COPY pom.xml .
COPY target/internal-packages/ /app/packages/

RUN mvn install:install-file \
    -Dfile=/app/packages/ai-stack-communication-utils-1.0-SNAPSHOT.jar \
    -DgroupId=pl.beone.ai \
    -DartifactId=ai-stack-communication-utils \
    -Dversion=1.0-SNAPSHOT \
    -Dpackaging=jar \
    -DgeneratePom=true
RUN mvn dependency:go-offline

COPY src ./src
RUN mvn package -DskipTests

# Use the official Alfresco base image with JRE 17 on Rocky Linux 8
FROM alfresco/alfresco-base-java:jre17-rockylinux8-202306121108

RUN dnf install -y glibc-langpack-en && \
    localedef -v -c -i en_US -f UTF-8 en_US.UTF-8 || true

ENV LANG=en_US.UTF-8
ENV LANGUAGE=en_US:en
ENV LC_ALL=en_US.UTF-8
ARG JAR_FILE=/build/target/ai-stack-alfresco-ai-rag-*.jar

ARG UID=10001

RUN yum install -y passwd && \
    # Create a non-login user with no home directory
    adduser \
        --comment "App User" \
        --home "/nonexistent" \
        --shell "/sbin/nologin" \
        --no-create-home \
        --uid "${UID}" \
        appuser && \
    # Lock the user account for security
    passwd --lock appuser && \
    # Clean up unnecessary files to reduce image size
    yum clean all && \
    rm -rf /var/cache/yum

USER appuser

WORKDIR /opt

COPY --from=build ${JAR_FILE} app.jar

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]


