FROM navikt/java:16
COPY build/libs/app.jar /app/
ENV JAVA_OPTS="-Djava.security.egd=file:/dev/./urandom"
