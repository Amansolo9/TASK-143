FROM eclipse-temurin:17-jdk AS builder

ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH="${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:/opt/gradle-8.5/bin"

RUN apt-get update && apt-get install -y --no-install-recommends \
    wget \
    unzip \
    && rm -rf /var/lib/apt/lists/*

# Install Gradle 8.5
RUN wget -q "https://services.gradle.org/distributions/gradle-8.5-bin.zip" -O /tmp/gradle.zip && \
    unzip -q /tmp/gradle.zip -d /opt && \
    rm /tmp/gradle.zip && \
    gradle --version

# Generate Gradle wrapper jar in an empty temp project (avoids classpath resolution)
RUN mkdir -p /tmp/wrapper-gen && \
    cd /tmp/wrapper-gen && \
    touch settings.gradle && \
    gradle wrapper --gradle-version 8.5 --no-daemon && \
    ls -la gradle/wrapper/gradle-wrapper.jar

# Download and install Android command-line tools
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    wget -q "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" -O /tmp/cmdline-tools.zip && \
    unzip -q /tmp/cmdline-tools.zip -d ${ANDROID_HOME}/cmdline-tools && \
    mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest && \
    rm /tmp/cmdline-tools.zip

# Accept licenses and install required SDK packages
RUN yes | sdkmanager --licenses > /dev/null 2>&1 && \
    sdkmanager \
    "platforms;android-34" \
    "build-tools;34.0.0" \
    "platform-tools"

WORKDIR /app

# Copy the generated wrapper jar
RUN mkdir -p gradle/wrapper
COPY gradle/wrapper/gradle-wrapper.properties gradle/wrapper/gradle-wrapper.properties
RUN cp /tmp/wrapper-gen/gradle/wrapper/gradle-wrapper.jar gradle/wrapper/gradle-wrapper.jar
COPY gradlew ./
RUN chmod +x gradlew

# Copy build configs for dependency caching
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY app/build.gradle.kts app/build.gradle.kts
COPY app/proguard-rules.pro app/proguard-rules.pro

# Create minimal source structure for dependency resolution
RUN mkdir -p app/src/main/java/com/learnmart/app && \
    mkdir -p app/src/main/res/values
COPY app/src/main/AndroidManifest.xml app/src/main/AndroidManifest.xml
COPY app/src/main/res/values/strings.xml app/src/main/res/values/strings.xml
COPY app/src/main/res/values/themes.xml app/src/main/res/values/themes.xml

# Pre-download dependencies
RUN ./gradlew dependencies --no-daemon 2>/dev/null || true

# Copy full source
COPY . .
RUN chmod +x gradlew

# Generate local.properties from ANDROID_HOME (not committed to repo)
RUN echo "sdk.dir=${ANDROID_HOME}" > local.properties

# Build the debug APK (increase memory for Kotlin compiler in container)
ENV GRADLE_OPTS="-Xmx4096m"
RUN ./gradlew assembleDebug --no-daemon --stacktrace

# Output stage
FROM eclipse-temurin:17-jre-alpine AS output
WORKDIR /output
COPY --from=builder /app/app/build/outputs/apk/debug/app-debug.apk ./learnmart-debug.apk
CMD ["echo", "LearnMart build complete. APK at /output/learnmart-debug.apk"]
