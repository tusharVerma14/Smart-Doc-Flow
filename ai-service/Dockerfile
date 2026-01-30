# Build stage
FROM gradle:8.5-jdk17 AS build

WORKDIR /app

# Copy gradle files
COPY build.gradle settings.gradle ./

# Copy source code
COPY src ./src

# Build the application
RUN gradle clean build -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:17-jre

# Install Tesseract OCR
# Install tesseract + language data
RUN apt-get update && apt-get install -y \
    tesseract-ocr \
    tesseract-ocr-eng \
    libtesseract-dev \
    libleptonica-dev \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy the built jar from build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Set Tesseract data path
ENV TESSDATA_PREFIX=/usr/share/tesseract-ocr/5/tessdata

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
