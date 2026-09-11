# Выкат в k3s Дымохода

Манифесты kustomize для namespace `karandash`. В кластер они пока не применялись.

| Сервис | Нода | Состояние | Секрет |
|---|---|---|---|
| `postgres` (StatefulSet, PVC 5 Gi) | `pythagoras`, РФ | персональные данные | `karandash-db` |
| `core` | `pythagoras`, РФ | единственный с доступом к базе | `karandash-db`, `karandash-core` |
| `agent-adapter` | `v749216.hosted-by-vdsina.com`, зарубежная | без состояния, `/tmp` в памяти | `karandash-agent` |
| `telegram-bot` (1 реплика, `Recreate`) | `v749216.hosted-by-vdsina.com`, зарубежная | без состояния, без Service | `karandash-bot` |
| RabbitMQ — общий, Дымохода (`chimney/rabbitmq`) | `pythagoras`, РФ | vhost `karandash` | пароли в `karandash-core`, `karandash-bot` |

Под получает только свой секрет, а kubelet ноды видит лишь секреты её подов — пароль базы на зарубежную ноду не попадает.

> **Применять только на `pythagoras` и только через `deploy/k8s/apply.sh`.** Скрипт ничего не пишет, пока не убедится, что это кластер Дымохода: в нём есть ноды с метками Карандаша, заведены секреты и vhost в брокере, а трафик между нодами шифруется. На рабочих машинах `kubectl` может смотреть в чужой кластер — например, в прод Строительного двора — и голый `kubectl apply -k` создал бы namespace там.

## Перед боевым выкатом

- [ ] **Трафик между нодами зашифрован.** k3s ставился с бэкендом flannel по умолчанию — VXLAN без шифрования. Через него идут текст и фото пользователей, сервисные токены и AMQP-соединение приёмщика с брокером, вместе с его паролем. Общий брокер этого не меняет: подключение к нему с зарубежной ноды проходит по той же сети. Проверка на `pythagoras`:
  ```sh
  ip -d link show flannel.1   # есть — открытый VXLAN
  ip link show flannel-wg     # есть — WireGuard
  grep -i flannel /etc/rancher/k3s/config.yaml
  ```
  WireGuard на весь кластер — `flannel-backend: wireguard-native` в `/etc/rancher/k3s/config.yaml`, UDP 51820 между нодами, перезапуск k3s на обеих нодах — закрывает и Дымоход. Альтернатива — TLS внутри Карандаша (доработка кода). `apply.sh` при VXLAN останавливается; пробный выкат без пользователей — только с `KARANDASH_ALLOW_PLAINTEXT_INTERNODE=1`.
- [ ] **Запас памяти на зарубежной ноде.** Там 2 vCPU и 2 ГБ, из которых уже заняты AI Service Дымохода (limit 1 Gi), Grafana, Promtail и k3s-agent. Поды Карандаша просят 704 Mi, их лимит — 1,4 Gi. Поэтому агент настроен на один вызов CLI за раз (`AGENT_MAX_CONCURRENT_CALLS=1`, limit 1 Gi; в простое он занимает около 256 Mi). После расширения ноды до 4 ГБ — `2` и 1280 Mi. После выката смотреть `kubectl top node`.
- [ ] **Доступ к брокеру Дымохода из namespace `karandash`.** Политика `allow-rabbitmq` Дымохода перечисляет свои поды и `ipBlock 0.0.0.0/0`. Если подключение из `karandash` всё же режется, добавить в неё `namespaceSelector` `kubernetes.io/metadata.name: karandash` — это правка в репозитории Дымохода.
- [ ] **Сетевые политики Карандаша** (`components/network-policies`) включены и проверены на кластере: без них изоляцию держат только разные секреты, а не сеть.
- [ ] **`AGENT_CLI=codex`** включается только после проверки на настоящем `codex exec`, что shell, файлы и сеть модели недоступны: сейчас реализация проверена лишь фейковым CLI. По умолчанию — `claude`.

Отдельно, к Дымоходу: сервис `rabbitmq` у него типа NodePort. AMQP (30672) и порты management/метрик открыты на внешних адресах нод без TLS. Vhost Карандаша от этого защищён только паролями его пользователей.

## Один раз

Метки нод (манифесты привязаны к ним через `nodeSelector`):

```sh
kubectl label node pythagoras karandash/region=ru
kubectl label node v749216.hosted-by-vdsina.com karandash/region=foreign
```

**RabbitMQ Дымохода** — отдельный vhost и два пользователя. Ядру — полные права только в своём vhost: оно объявляет обменники и очереди. Приёмщику — только чтение своей очереди. Данные брокера лежат на PVC, поэтому vhost и пользователи переживают перезапуск.

```sh
kubectl -n chimney exec statefulset/rabbitmq -- rabbitmqctl add_vhost karandash
kubectl -n chimney exec statefulset/rabbitmq -- rabbitmqctl add_user karandash-core '<пароль из karandash-core>'
kubectl -n chimney exec statefulset/rabbitmq -- rabbitmqctl set_permissions -p karandash karandash-core '.*' '.*' '.*'
kubectl -n chimney exec statefulset/rabbitmq -- rabbitmqctl add_user karandash-bot '<пароль из karandash-bot>'
kubectl -n chimney exec statefulset/rabbitmq -- rabbitmqctl set_permissions -p karandash karandash-bot '^$' '^$' '^q\.bot\.events$'
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

Проверка:

```sh
kubectl -n karandash get pods -o wide        # поды на нужных нодах
kubectl -n karandash logs deploy/telegram-bot # нет 401/409 от Telegram
kubectl -n karandash exec deploy/core -- curl -s localhost:8080/actuator/health/readiness
kubectl -n chimney exec statefulset/rabbitmq -- rabbitmqctl -q list_queues -p karandash name consumers
kubectl top node                              # запас памяти на зарубежной ноде
```

У `q.bot.events` должен быть один потребитель — приёмщик. Очереди объявляет ядро при старте.

Readiness агента включает проверку CLI: если учётных данных нет или провайдер отклонил ключ в последнем вызове, под становится not ready. Истёкший ключ виден в `kubectl get pods`, а не только по молчанию бота.

## Сетевые политики

`components/network-policies` разрешает только нужные входящие соединения: базу — ядру, ядро — приёмщику, агента — ядру. Политику брокера ведёт Дымоход. По умолчанию политики Карандаша выключены: в кластере Дымохода k3s/flannel уже неверно обрабатывал deny-all. Включаются раскомментированием `components` в `kustomization.yaml` после проверки на кластере.
