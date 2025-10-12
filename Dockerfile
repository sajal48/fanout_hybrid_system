# Use Eclipse Temurin JRE 17 (slim variant for smaller image)
FROM eclipse-temurin:17-jre-alpine

# Set working directory
WORKDIR /app

# Copy the JAR file from target directory
COPY target/hybrid-feed-system-1.0.0-SNAPSHOT.jar app.jar

# Expose port 8080
EXPOSE 8080

# Run the JAR file
ENTRYPOINT ["java", "-jar", "app.jar"]
