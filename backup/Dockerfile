# Образ для ночных копий любого сервиса кластера: клиент PostgreSQL, клиент S3, openssl для шифрования
# и ssh-клиент для выгрузки на сервер копий. Клиент S3 берём из официального образа MinIO:
# публичная ссылка dl.min.io отдаёт 410, а ночью джоб в интернет ходить не должен вовсе.
FROM quay.io/minio/mc:RELEASE.2025-08-13T08-35-41Z AS mc

FROM postgres:17-alpine

RUN apk add --no-cache openssl ca-certificates openssh-client

COPY --from=mc /usr/bin/mc /usr/local/bin/mc

# Тот же непривилегированный пользователь, что и у официального образа postgres.
USER 70
