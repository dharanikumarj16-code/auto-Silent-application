# Multi-stage Dockerfile for building Auto Silent Pro Android APK
FROM eclipse-temurin:21-jdk

# Set environment variables
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    wget \
    unzip \
    git \
    && rm -rf /var/lib/apt/lists/*

# Download and install Android Command Line Tools
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/cmdline-tools.zip && \
    unzip -q /tmp/cmdline-tools.zip -d ${ANDROID_HOME}/cmdline-tools && \
    mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest && \
    rm /tmp/cmdline-tools.zip

# Accept Android licenses and install platform tools + compile SDK 35
RUN yes | sdkmanager --licenses && \
    sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"

# Set working directory
WORKDIR /app

# Copy project files
COPY . .

# Grant execute permission to Gradle wrapper
RUN chmod +x gradlew

# Run unit tests and assemble Debug APK
RUN ./gradlew testDebugUnitTest assembleDebug

# Output APK path will be at: /app/app/build/outputs/apk/debug/app-debug.apk
CMD ["cp", "app/build/outputs/apk/debug/app-debug.apk", "/output/AutoSilentPro.apk"]
