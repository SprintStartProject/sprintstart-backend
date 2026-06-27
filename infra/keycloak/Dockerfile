FROM quay.io/keycloak/keycloak:26.0.0

COPY themes/*.jar /opt/keycloak/providers/

RUN /opt/keycloak/bin/kc.sh build