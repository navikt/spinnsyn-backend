FROM navikt/java:14
COPY build/libs/*.jar app.jar
ENV JAVA_OPTS='-Dlogback.configurationFile=logback.xml'
