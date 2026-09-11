# Выкат в k3s Дымохода

Манифесты kustomize для namespace `karandash`. В кластер они пока не применялись.

| Сервис | Нода | Состояние | Секрет |
|---|---|---|---|
| `postgres` (StatefulSet, PVC 5 Gi) | `pythagoras`, РФ | персональные данные | `karandash-db` |
| `rabbitmq` (StatefulSet, PVC 1 Gi) | `pythagoras`, РФ | очереди событий | `karandash-db` |
| `core` | `pythagoras`, РФ | единственный с доступом к базе | `karandash-db`, `karandash-core` |
| `agent-adapter` | `v749216.hosted-by-vdsina.com`, зарубежная | без состояния, `/tmp` в памяти | `karandash-agent` |
| `telegram-bot` (1 реплика, `Recreate`) | `v749216.hosted-by-vdsina.com`, зарубежная | без состояния, без Service | `karandash-bot` |

Под получает только свой секрет, а kubelet ноды видит лишь секреты её подов — пароль базы на зарубежную ноду не попадает.

> **Применять только на `pythagoras` и только через `deploy/k8s/apply.sh`.** Скрипт сначала проверяет, что в текущем кластере есть ноды с метками Карандаша, и без них ничего не пишет. На рабочих машинах `kubectl` может смотреть в чужой кластер — например, в прод Строительного двора — и голый `kubectl apply -k` создал бы namespace там.

## Перед боевым выкатом

- [ ] **Трафик между нодами зашифрован.** k3s ставился с бэкендом flannel по умолчанию — VXLAN без шифрования, а текст, фото и сервисные токены идут между странами. Проверка на `pythagoras`:
  ```sh
  ip -d link show flannel.1   # есть — открытый VXLAN
  ip link show flannel-wg     # есть — WireGuard
  grep -i flannel /etc/rancher/k3s/config.yaml
  ```
  Два варианта:
  - WireGuard на весь кластер: `flannel-backend: wireguard-native` в `/etc/rancher/k3s/config.yaml`, UDP 51820 между нодами, перезапуск k3s на обеих нодах. Закрывает и Дымоход, но даёт короткий простой сети подов.
  - TLS только внутри Карандаша: доработка кода и сертификаты.

  `apply.sh` при VXLAN останавливается. Пробный выкат без пользователей — только с `KARANDASH_ALLOW_PLAINTEXT_INTERNODE=1`.
- [ ] **Запас памяти на зарубежной ноде.** Там 2 vCPU и 2 ГБ, из которых уже заняты AI Service Дымохода (limit 1 Gi), Grafana, Promtail и k3s-agent. Поды Карандаша просят 704 Mi, их лимит — 1,4 Gi. Поэтому агент настроен на один вызов CLI за раз (`AGENT_MAX_CONCURRENT_CALLS=1`, limit 1 Gi; в простое он занимает около 256 Mi). После расширения ноды до 4 ГБ — `2` и 1280 Mi. После выката смотреть `kubectl top node`.
- [ ] **Сетевые политики** (`components/network-policies`) включены и проверены на кластере: без них изоляцию держат только разные секреты, а не сеть.
- [ ] **Пользователь RabbitMQ `karandash-bot`** создан (см. ниже) и сохранён в бэкапе или definitions: при пересоздании брокера приёмщик иначе потеряет доступ к очереди.
- [ ] **`AGENT_CLI=codex`** включается только после проверки на настоящем `codex exec`, что shell, файлы и сеть модели недоступны: сейчас реализация проверена лишь фейковым CLI. По умолчанию — `claude`.

## Один раз

Метки нод (манифесты привязаны к ним через `nodeSelector`):

```sh
kubectl label node pythagoras karandash/region=ru
kubectl label node v749216.hosted-by-vdsina.com karandash/region=foreign
```

Реестр: зарубежная нода уже тянет `localhost:5000` с `pythagoras` через `/etc/rancher/k3s/registries.yaml` — делать ничего не нужно.

Секреты — вне git и вне ArgoCD, как `chimney-secrets` у Дымохода:
1. `kubectl apply -f deploy/k8s/namespace.yaml`.
2. Скопировать `secrets.example.yaml` за пределы репозитория и заполнить. Пароли и сервисные токены — `openssl rand -hex 32`. `CORE_SERVICE_TOKEN` и `AGENT_SERVICE_TOKEN` совпадают в секретах обеих сторон.
3. Ключ CLI — отдельный API-ключ Anthropic только для Карандаша: его можно отозвать, не задев ничего другого. `CLAUDE_CODE_OAUTH_TOKEN` и `CODEX_API_KEY` оставить пустыми. Секрет из namespace `chimney` Карандашу недоступен.
4. `kubectl apply -f <заполненный файл>`.

Бот в Telegram — токен от `@BotFather` в `karandash-bot/TELEGRAM_BOT_TOKEN`. Webhook у бота должен быть выключен: при нём `getUpdates` отвечает 409.

## Выкат

На `pythagoras`, в корне репозитория:

```sh
for service in core agent-adapter telegram-bot; do
  docker build -f "$service/Dockerfile" -t "localhost:5000/karandash-$service:latest" . \
    && docker push "localhost:5000/karandash-$service:latest"
done

sh deploy/k8s/apply.sh
```

После первого старта `rabbitmq` — пользователь для приёмщика, только чтение своей очереди:

```sh
kubectl -n karandash exec statefulset/rabbitmq -- rabbitmqctl add_user karandash-bot '<пароль из karandash-bot>'
kubectl -n karandash exec statefulset/rabbitmq -- rabbitmqctl set_permissions -p / karandash-bot '^$' '^$' '^q\.bot\.events$'
```

Проверка:

```sh
kubectl -n karandash get pods -o wide        # поды на нужных нодах
kubectl -n karandash logs deploy/telegram-bot # нет 401/409 от Telegram
kubectl -n karandash exec deploy/core -- curl -s localhost:8080/actuator/health/readiness
kubectl top node                              # запас памяти на зарубежной ноде
```

Readiness агента включает проверку CLI: если учётных данных нет или провайдер отклонил ключ в последнем вызове, под становится not ready. Истёкший ключ виден в `kubectl get pods`, а не только по молчанию бота.

## Сетевые политики

`components/network-policies` разрешает только нужные входящие соединения: базу — ядру, брокер — ядру и приёмщику, ядро — приёмщику, агента — ядру. По умолчанию политики выключены: в кластере Дымохода k3s/flannel уже неверно обрабатывал deny-all. Включаются раскомментированием `components` в `kustomization.yaml` после проверки на кластере.
