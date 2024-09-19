FROM gcr.io/distroless/java21-debian12@sha256:75ad71d93a470a97153c3a638828203bdf3f4207c1338ce111b43751dba38c7e

ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseParallelGC -XX:ActiveProcessorCount=2"

COPY build/libs/app.jar /app/
WORKDIR /app
CMD ["app.jar"]
