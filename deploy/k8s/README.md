# Выкат в k3s Дымохода

Манифесты kustomize для namespace `karandash`. В кластер они пока не применялись: зарубежная нода не определена.

| Сервис | Нода | Состояние | Секрет |
|---|---|---|---|
| `postgres` (StatefulSet, PVC 5 Gi) | РФ | персональные данные | `karandash-db` |
| `rabbitmq` (StatefulSet, PVC 1 Gi) | РФ | очереди событий | `karandash-db` |
| `core` | РФ | единственный с доступом к базе | `karandash-db`, `karandash-core` |
| `agent-adapter` | зарубежная | без состояния, `/tmp` в памяти | `karandash-agent` |
| `telegram-bot` (1 реплика, `Recreate`) | зарубежная | без состояния, без Service | `karandash-bot` |

Под получает только свой секрет, а kubelet ноды видит лишь секреты её подов — пароль базы на зарубежную ноду не попадает.

## Что нужно от владельца

1. **Метки нод.** В манифестах метки-заглушки:
   ```sh
   kubectl label node <нода-РФ> karandash/region=ru
   kubectl label node <зарубежная-нода> karandash/region=foreign
   ```
   По `k8s/INFRASTRUCTURE.md` Дымохода кандидаты — `pythagoras` (`chimney/role=main`, РФ, Telegram оттуда заблокирован) и `v749216.hosted-by-vdsina.com` (`chimney/role=ai`, там уже работают Telegram и Anthropic API). У ai-ноды 2 vCPU и 2 ГБ памяти, и на ней уже Grafana и AI Service; приёмщик и агент просят ещё около 0,7 ГБ и могут вырасти до 1,7 ГБ.
2. **Шифрование между нодами.** Трафик ядро ↔ агент и ядро ↔ приёмщик ↔ RabbitMQ идёт между странами по сети кластера. Если бэкенд flannel не `wireguard-native`, этот трафик открыт — нужен WireGuard или TLS между сервисами.
3. **Реестр.** Образы `localhost:5000/karandash-*:latest`. Зарубежная нода должна тянуть `localhost:5000` с зеркала на `pythagoras` через `/etc/rancher/k3s/registries.yaml` — так уже устроено для ai-ноды Дымохода.
4. **Секреты** (значения — только в кластере): скопировать `secrets.example.yaml` вне репозитория, заполнить, `kubectl apply -f`. CLI агента — `ANTHROPIC_API_KEY`; подписочный `CLAUDE_CODE_OAUTH_TOKEN` для сервиса с чужими пользователями может не соответствовать условиям подписки.
5. **Бот в Telegram** — токен от `@BotFather` в `karandash-bot/TELEGRAM_BOT_TOKEN`. Webhook у бота должен быть выключен: при нём `getUpdates` отвечает 409.

## Порядок выката

На ноде РФ, в корне репозитория:

```sh
for service in core agent-adapter telegram-bot; do
  docker build -f "$service/Dockerfile" -t "localhost:5000/karandash-$service:latest" . \
    && docker push "localhost:5000/karandash-$service:latest"
done

kubectl apply -f /путь/вне/git/secrets.yaml
kubectl apply -k deploy/k8s
```

Пользователь RabbitMQ для приёмщика — только чтение своей очереди. Создаётся один раз, после старта `rabbitmq`:

```sh
kubectl -n karandash exec statefulset/rabbitmq -- rabbitmqctl add_user karandash-bot '<пароль из karandash-bot>'
kubectl -n karandash exec statefulset/rabbitmq -- rabbitmqctl set_permissions -p / karandash-bot '^$' '^$' '^q\.bot\.events$'
```

Проверка:

```sh
kubectl -n karandash get pods -o wide        # поды на нужных нодах
kubectl -n karandash logs deploy/telegram-bot # нет 401/409 от Telegram
kubectl -n karandash exec deploy/core -- curl -s localhost:8080/actuator/health/readiness
```

Readiness агента включает проверку CLI: если учётных данных нет или провайдер отклонил ключ в последнем вызове, под становится not ready. Истёкший ключ виден в `kubectl get pods`, а не только по молчанию бота.

## Сетевые политики

`components/network-policies` разрешает только нужные входящие соединения: базу — ядру, брокер — ядру и приёмщику, ядро — приёмщику, агента — ядру. По умолчанию политики выключены: в кластере Дымохода k3s/flannel уже неверно обрабатывал deny-all. Включаются раскомментированием `components` в `kustomization.yaml` после проверки на кластере.
