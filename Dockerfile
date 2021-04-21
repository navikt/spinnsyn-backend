FROM redboxoss/scuttle:1.3.3 AS scuttle
FROM navikt/java:14
COPY --from=scuttle /scuttle /bin/scuttle

COPY build/libs/app.jar /app/

ENV JAVA_OPTS="-Djava.security.egd=file:/dev/./urandom"
ENV ENVOY_ADMIN_API=http://127.0.0.1:15000

ENTRYPOINT ["scuttle", "/dumb-init", "--", "/entrypoint.sh"]
