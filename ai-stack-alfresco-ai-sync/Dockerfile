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

FROM alfresco/alfresco-base-java:jre17-rockylinux8-202302221525


RUN dnf install -y glibc-langpack-en && \
    localedef -v -c -i en_US -f UTF-8 en_US.UTF-8 || true

ENV LANG=en_US.UTF-8
ENV LANGUAGE=en_US:en
ENV LC_ALL=en_US.UTF-8

ARG JAR_FILE=/build/target/ai-stack-alfresco-ai-sync-*.jar

WORKDIR /opt/app

RUN groupadd -r alfrescoaisync && useradd -r -g alfrescoaisync alfrescoaisync
RUN mkdir -p /var/log/alfresco-ai-sync && \
    chown -R alfrescoaisync:alfrescoaisync /var/log/alfresco-ai-sync

COPY --from=build ${JAR_FILE} app.jar
RUN chown alfrescoaisync:alfrescoaisync app.jar && chmod 400 app.jar

USER alfrescoaisync

ENTRYPOINT ["java", "-jar", "app.jar"]
