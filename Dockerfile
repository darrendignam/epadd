FROM maven:3.9-eclipse-temurin-11 AS build
WORKDIR /src

COPY pom.xml pom-common.xml ./
COPY lib ./lib
RUN mvn -B -ntp -f pom.xml dependency:go-offline -Dlicense.skipDownloadLicenses=true || true

COPY src ./src
COPY WebContent ./WebContent
RUN mvn -B -ntp -f pom.xml package \
        -DskipTests \
        -Dlicense.skipDownloadLicenses=true \
 && mv target/epadd-*.war /epadd.war


FROM tomcat:9.0-jdk11-temurin AS runtime

ARG EPADD_UID=10001
ARG EPADD_GID=10001

RUN groupadd --gid ${EPADD_GID} epadd \
 && useradd --uid ${EPADD_UID} --gid epadd --home-dir /data/home --shell /usr/sbin/nologin epadd \
 && rm -rf /usr/local/tomcat/webapps/* \
 && mkdir -p /data/home /data/repos /data/settings /data/config /data/logs \
 && chown -R epadd:epadd /data /usr/local/tomcat

COPY --from=build --chown=epadd:epadd /epadd.war /usr/local/tomcat/webapps/epadd.war
COPY --chown=epadd:epadd docker/ROOT /usr/local/tomcat/webapps/ROOT
COPY --chmod=0755 docker/entrypoint.sh /usr/local/bin/epadd-entrypoint

ENV EPADD_MODE=appraisal \
    EPADD_LANGUAGE=en \
    EPADD_ADMIN_CONTACT= \
    EPADD_BASE_DIR=/data/repos \
    EPADD_SETTINGS_DIR=/data/settings \
    EPADD_PROPERTIES_FILE=/data/config/epadd.properties \
    EPADD_LOG_FILE=/data/logs/epadd.log \
    EPADD_JAVA_OPTS="-Xmx4g"

VOLUME ["/data/repos", "/data/settings", "/data/config", "/data/logs"]
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=5 \
    CMD curl -fsS -o /dev/null http://localhost:8080/epadd/ || exit 1

USER epadd
ENTRYPOINT ["epadd-entrypoint"]
CMD ["catalina.sh", "run"]
