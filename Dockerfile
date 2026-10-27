# Stage 1: Build source code & đóng gói JAR
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copy Maven Wrapper & file cấu hình pom.xml
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x ./mvnw && ./mvnw dependency:go-offline -B

# Copy toàn bộ mã nguồn và build ứng dụng (bỏ qua unit test khi build image)
COPY src/ ./src/
RUN ./mvnw clean package -DskipTests

# Stage 2: Runtime image siêu nhẹ tối ưu bảo mật
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Tạo non-root user để chạy app an toàn
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy file JAR từ stage builder
COPY --from=builder /app/target/*.jar app.jar
RUN chown -R appuser:appgroup /app

USER appuser
EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
