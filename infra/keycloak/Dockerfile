FROM quay.io/keycloak/keycloak:26.0.0

COPY event-spi-1.0-SNAPSHOT.jar /opt/keycloak/providers/

RUN /opt/keycloak/bin/kc.sh build