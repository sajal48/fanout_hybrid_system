# Use Eclipse Temurin JDK 21 (slim variant with Shenandoah GC support)
FROM eclipse-temurin:21-jdk-alpine

# Set working directory
WORKDIR /app

# Copy the JAR file from target directory
COPY target/hybrid-feed-system-1.0.0-SNAPSHOT.jar app.jar

# Expose port 8080
EXPOSE 8080

# Run with Shenandoah GC enabled
# Shenandoah flags:
# -XX:+UnlockExperimentalVMOptions: Enable experimental features
# -XX:+UseShenandoahGC: Use Shenandoah GC
# -XX:ShenandoahGCHeuristics=adaptive: Adaptive heap sizing
# -Xmx2g: Max heap size 2GB
# -Xms2g: Initial heap size 2GB
ENTRYPOINT ["java", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseShenandoahGC", "-XX:ShenandoahGCHeuristics=adaptive", "-Xmx2g", "-Xms2g", "-jar", "app.jar"]
