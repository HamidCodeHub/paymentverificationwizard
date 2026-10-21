# Multi-arch like the style-match image: both base images publish linux/arm64, the ONNX Runtime jar
# bundles a linux-aarch64 native, and Ubuntu ships tesseract-ocr for arm64.

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Dependencies resolve in their own layer so a source-only change does not re-download Spring.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests


# Noble (24.04), not jammy: jammy's tesseract is 4.1; noble ships 5.3.4, the version the thresholds were
# measured with.
FROM eclipse-temurin:17-jre-noble
WORKDIR /app

# tesseract-ocr brings the engine (+ English/OSD data, unused). The Persian model is tessdata_best's
# fas.traineddata, mounted with the other models - the distro's tesseract-ocr-fas is the smaller "fast" model.
RUN apt-get update \
 && apt-get install -y --no-install-recommends tesseract-ocr curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd -r -u 1001 -m -d /app -s /sbin/nologin app

COPY --from=build /build/target/receipt-gate-*.jar app.jar

# Mounted at runtime, never baked into the image:
#   /app/models      CLIP ONNX (350 MB), tessdata/fas.traineddata, and the embedding cache (read-write)
#   /app/references  receipts/ and non-receipts/ examples (read-only). Real receipts carry personal data.
RUN mkdir -p /app/models /app/references && chown -R app:app /app

USER app
EXPOSE 8086

ENV JAVA_OPTS="-XX:MaxRAMPercentage=50 -XX:+UseSerialGC -Djava.awt.headless=true"
ENV SPRING_PROFILES_ACTIVE=prod

# First start embeds ~135 reference images (~30 s on 2 cores); later starts read the cache.
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
  CMD curl -fsS http://localhost:8086/api/v1/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
