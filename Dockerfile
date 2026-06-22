# 1. Aşama: Projeyi Maven ile derle
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app
# Bağımlılıkları ve kaynak kodları kopyala
COPY pom.xml .
COPY src ./src
# Testleri atlayarak projeyi paketle (.jar dosyasını oluştur)
RUN mvn clean package -DskipTests

# 2. Aşama: Sadece JRE kullanarak hafif bir imaj oluştur
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# İlk aşamada üretilen .jar dosyasını al
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
# Uygulamayı başlat
ENTRYPOINT ["java", "-jar", "app.jar"]