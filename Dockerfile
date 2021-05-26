FROM navikt/java:14
COPY build/libs/app.jar /app/
ENV JAVA_OPTS="-Djava.security.egd=file:/dev/./urandom"
