# Imagem única (API + front Angular). Build em 3 estágios: Angular -> Maven -> JRE.
# docker build -t checklist-auditoria .
# docker run -p 8080:8080 checklist-auditoria          (sobe em modo demonstração)

FROM node:24-alpine AS front
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npx ng build --configuration production

FROM eclipse-temurin:21-jdk-alpine AS back
WORKDIR /app/backend
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY backend/src src
# O pom copia ../frontend/dist/frontend/browser para static/.
COPY --from=front /app/frontend/dist /app/frontend/dist
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app && mkdir -p /app/dados && chown app:app /app/dados
USER app
COPY --from=back /app/backend/target/checklist-auditoria.jar app.jar
ENV SPRING_PROFILES_ACTIVE=demo \
    PORTA=8080 \
    ARMAZENAMENTO_DIR=/app/dados/anexos \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
