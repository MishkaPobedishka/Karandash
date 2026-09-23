# Образ ночной копии базы: клиент PostgreSQL, клиент S3 и openssl для шифрования дампа.
# Клиент S3 берём из официального образа MinIO — ночью джоб в интернет за бинарниками не ходит.
FROM quay.io/minio/mc:RELEASE.2025-08-13T08-35-41Z AS mc

FROM postgres:17-alpine

RUN apk add --no-cache openssl ca-certificates

COPY --from=mc /usr/bin/mc /usr/local/bin/mc

# Тот же непривилегированный пользователь, что и у официального образа postgres.
USER 70
