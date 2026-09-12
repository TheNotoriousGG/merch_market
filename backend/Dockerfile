FROM gradle:9.6.1-jdk25-ubi10@sha256:f28ea16270b96c12c3b3f6a6f1ea206e8e6f8e7aa3d1297f4974975ea6da874b AS extractor

WORKDIR /workspace

ARG JAR_FILE=build/libs/application.jar
COPY ${JAR_FILE} application.jar

RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM gcr.io/distroless/java25-debian13:nonroot@sha256:54bb9e269b6f1866d91055fd55dc39baee1914da01bb03eeb44c5b268e77176a

ARG BUILD_CREATED
ARG VCS_REF
ARG VCS_URL
ARG VERSION

LABEL org.opencontainers.image.created="${BUILD_CREATED}" \
      org.opencontainers.image.description="Production backend for Amra Shop" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.source="${VCS_URL}" \
      org.opencontainers.image.title="amra-merch-market-backend" \
      org.opencontainers.image.vendor="Amra Shop" \
      org.opencontainers.image.version="${VERSION}"

WORKDIR /application

COPY --from=extractor --chown=nonroot:nonroot /workspace/extracted/dependencies/ ./
COPY --from=extractor --chown=nonroot:nonroot /workspace/extracted/spring-boot-loader/ ./
COPY --from=extractor --chown=nonroot:nonroot /workspace/extracted/snapshot-dependencies/ ./
COPY --from=extractor --chown=nonroot:nonroot /workspace/extracted/application/ ./

# Distroless guarantees the named nonroot identity (UID/GID 65532) across supported architectures.
# hadolint ignore=DL3066
USER nonroot:nonroot

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "application.jar"]
