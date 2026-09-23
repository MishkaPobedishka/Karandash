# Выкат в k3s Дымохода

Манифесты kustomize для namespace `karandash`. В кластер они пока не применялись.

| Сервис | Нода | Состояние | Секрет |
|---|---|---|---|
| `postgres` (StatefulSet, PVC 5 Gi) | `pythagoras`, РФ | персональные данные | `karandash-db` |
| `core` | `pythagoras`, РФ | единственный с доступом к базе | `karandash-db`, `karandash-core` |
| `agent-adapter` | `n8n-hassle` (Валера, Алматы), зарубежная | `/tmp` в памяти; постоянный только каталог входа Codex | `karandash-agent`, `karandash-codex` |
| `telegram-bot` (1 реплика, `Recreate`) | `n8n-hassle` (Валера, Алматы), зарубежная | без состояния, без Service | `karandash-bot` |
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
- [ ] **Зарубежная нода подключена к кластеру.** Это `n8n-hassle` (Валера, Алматы): 4 vCPU, 7,8 ГБ, свободно около 4,3 ГБ — с запасом под два параллельных вызова агента (`AGENT_MAX_CONCURRENT_CALLS=2`, limit 1280 Mi; в простое агент занимает около 256 Mi). Порядок подключения — ниже, раздел «Зарубежная нода». На сервере живут n8n, Jitsi и VPN: после выката смотреть `kubectl top node` и что рабочим сервисам хватает памяти.
- [ ] **Доступ к брокеру Дымохода из namespace `karandash`.** Политика `allow-rabbitmq` Дымохода перечисляет свои поды и `ipBlock 0.0.0.0/0`. Если подключение из `karandash` всё же режется, добавить в неё `namespaceSelector` `kubernetes.io/metadata.name: karandash` — это правка в репозитории Дымохода.
- [ ] **Сетевые политики Карандаша** (`components/network-policies`) включены и проверены на кластере: без них изоляцию держат только разные секреты, а не сеть.
- [ ] **Первый администратор бота назван.** Бот в бета-тесте: новый пользователь видит приглашение и кнопку «Подать заявку», доступ открывает администратор. Первый администратор берётся из `ADMIN_TELEGRAM_IDS` в секрете `karandash-core` (номера через запятую) — внутри бота выдать себе права некому. Свой номер человек узнаёт командой `/id`. Пока список пуст, заявки некому рассматривать:
  ```sh
  kubectl -n karandash patch secret karandash-core --type merge \
    -p "{\"stringData\":{\"ADMIN_TELEGRAM_IDS\":\"123456789\"}}"
  kubectl -n karandash rollout restart deploy/core
  ```
- [ ] **Учётные данные модели.** Сейчас в манифестах `AGENT_CLI=codex` с подписочным входом ChatGPT: секрет `karandash-codex` с файлом `auth.json`, постоянный каталог `/var/lib/karandash/codex` на томе. Проверено на codex-cli 0.154.0: модель отвечает по контракту, а на попытку выполнить команду из пользовательского текста событий запуска команд не появляется. Помните про ротацию: ту же копию `auth.json` нельзя держать и на личной машине, и в кластере. Альтернатива — `AGENT_CLI=claude` с ключом Anthropic в `karandash-agent`.

Отдельно, к Дымоходу: сервис `rabbitmq` у него типа NodePort. AMQP (30672) и порты management/метрик открыты на внешних адресах нод без TLS. Vhost Карандаша от этого защищён только паролями его пользователей.

## Один раз

Метки нод (манифесты привязаны к ним через `nodeSelector`):

```sh
kubectl label node pythagoras karandash/region=ru
kubectl label node n8n-hassle karandash/region=foreign
```

Метку `karandash/region=foreign` держит ровно одна нода: поды Карандаша поедут туда, а нода `v749216.hosted-by-vdsina.com` остаётся Дымоходу.

**RabbitMQ Дымохода** — отдельный vhost и два пользователя. Ядру — полные права только в своём vhost: оно объявляет обменники и очереди. Приёмщику — только чтение своей очереди. Данные брокера лежат на PVC, поэтому vhost и пользователи переживают перезапуск.

```sh
kubectl -n chimney exec statefulset/rabbitmq -- rabbitmqctl add_vhost karandash
kubectl -n chimney exec statefulset/rabbitmq -- rabbitmqctl add_user karandash-core '<пароль из karandash-core>'
kubectl -n chimney exec statefulset/rabbitmq -- rabbitmqctl set_permissions -p karandash karandash-core '.*' '.*' '.*'
kubectl -n chimney exec statefulset/rabbitmq -- rabbitmqctl add_user karandash-bot '<пароль из karandash-bot>'
kubectl -n chimney exec statefulset/rabbitmq -- rabbitmqctl set_permissions -p karandash karandash-bot '^$' '^$' '^q\.bot\.events$'
```

Секреты — вне git и вне ArgoCD, как `chimney-secrets` у Дымохода:
1. `kubectl apply -f deploy/k8s/namespace.yaml`.
2. Скопировать `secrets.example.yaml` за пределы репозитория и заполнить. Пароли и сервисные токены — `openssl rand -hex 32`. `CORE_SERVICE_TOKEN` и `AGENT_SERVICE_TOKEN` совпадают в секретах обеих сторон.
3. Ключ CLI — отдельный API-ключ Anthropic только для Карандаша: его можно отозвать, не задев ничего другого. `CLAUDE_CODE_OAUTH_TOKEN` и `CODEX_API_KEY` оставить пустыми. Секрет из namespace `chimney` Карандашу недоступен.
4. `kubectl apply -f <заполненный файл>`.

Бот в Telegram — токен от `@BotFather` в `karandash-bot/TELEGRAM_BOT_TOKEN`. Webhook у бота должен быть выключен: при нём `getUpdates` отвечает 409.

## Зарубежная нода: подключение Валеры

Валера — `n8n-hassle.steep-man.ru` (199.189.255.107, Алматы, Ubuntu 24.04). На нём работают n8n, Jitsi, синхронизация Обсидиана, документация и VPN — их трогать нельзя. Проверено 12.09 только чтением: 4 vCPU, 7,8 ГБ памяти (свободно около 4,3 ГБ), 30 ГБ диска; порты 6443, 8472, 10250 и 51820 свободны; модуль WireGuard в ядре есть; `net.ipv4.ip_forward=1`; время синхронизировано; `84.38.189.217:6443` и `:5000` с него доступны; k3s не установлен.

Что учесть:

- ufw на Валере: `deny (routed)`, а у Docker политика `FORWARD DROP`. Без правил из шага 2 поды не смогут общаться.
- Порты 80 и 443 заняты Caddy и hysteria, поэтому нода подключается **агентом**: свой ingress агент не поднимает.
- NodePort-сервисы Дымохода (например, RabbitMQ на 30672) после подключения начнут слушать и на Валере. Снаружи их закрывает ufw — не открывайте эти порты.
- Реестр образов `localhost:5000` доступен по HTTP без авторизации через интернет: на маршруте образ можно подменить. Пока это не закрыто, для Карандаша безопаснее собирать образы прямо на Валере и импортировать в k3s:
  ```sh
  sudo docker build -f agent-adapter/Dockerfile -t localhost:5000/karandash-agent-adapter:latest .
  sudo docker save localhost:5000/karandash-agent-adapter:latest | sudo k3s ctr images import -
  ```
  и поставить подам `imagePullPolicy: IfNotPresent`. Иначе — закрыть реестр htpasswd и TLS.

Порядок. Каждый шаг проверять до перехода к следующему.

1. **WireGuard на кластере — на `pythagoras`.** В `/etc/rancher/k3s/config.yaml` добавить `flannel-backend: wireguard-native`, разрешить UDP 51820 с адреса Валеры, затем `sudo systemctl restart k3s`. Проверка: `ip link show flannel-wg`. Сеть подов кластера на время перезапуска прервётся, поэтому делать в тихое окно. На ноде `v749216` после этого — `sudo systemctl restart k3s-agent`. Если поды перестали видеть друг друга, ноды нужно перезагрузить: старый интерфейс `flannel.1` остаётся.
2. **ufw на Валере.**
   ```sh
   sudo ufw allow from 84.38.189.217 to any port 51820 proto udp   # flannel WireGuard
   sudo ufw allow from 84.38.189.217 to any port 10250 proto tcp   # kubelet: логи, exec, метрики
   sudo ufw route allow from 10.42.0.0/16
   sudo ufw route allow to 10.42.0.0/16
   ```
   Если кластер остаётся на VXLAN, вместо 51820 открыть 8472/udp.
3. **Токен ноды** — на `pythagoras`: `sudo cat /var/lib/rancher/k3s/server/node-token`. Это секрет: в репозиторий и в переписку не попадает.
4. **Агент на Валере.** Версию k3s взять ту же, что на `pythagoras` (`k3s --version`):
   ```sh
   curl -sfL https://get.k3s.io | INSTALL_K3S_VERSION='<версия с pythagoras>' \
     K3S_URL=https://84.38.189.217:6443 sh -s - agent \
     --token-file /root/.k3s-token \
     --node-name n8n-hassle \
     --node-label karandash/region=foreign \
     --node-taint karandash=only:NoSchedule
   ```
   Токен кладётся файлом, чтобы не светиться в списке процессов: на Валере `sudo install -m600 /dev/stdin /root/.k3s-token`, на вход — содержимое `/var/lib/rancher/k3s/server/node-token` с `pythagoras`.

   Метка запрета `karandash=only:NoSchedule` оставляет ноду только Карандашу. Без неё туда приедут DaemonSet-ы Дымохода: `node-exporter` и `promtail` — последний увёз бы логи n8n, Jitsi и VPN в РФ, — а также `svclb-traefik`, который пытается занять порты 80 и 443, уже занятые Caddy и hysteria. Поды Карандаша эту метку терпят, это прописано в их манифестах. Если позже понадобится мониторинг Валеры в Дымоходе, его DaemonSet-ам нужно добавить toleration.
   Если kubelet не стартует из-за свопа — добавить `--kubelet-arg=fail-swap-on=false`.
5. **Реестр** (если образы всё же тянутся с `pythagoras`) — `/etc/rancher/k3s/registries.yaml` на Валере, как на ноде `v749216`:
   ```yaml
   mirrors:
     "localhost:5000":
       endpoint:
         - "http://84.38.189.217:5000"
   ```
   затем `sudo systemctl restart k3s-agent`.
6. **Проверка с `pythagoras`:**
   ```sh
   kubectl get nodes -o wide
   kubectl get node n8n-hassle --show-labels
   kubectl top node
   ```
   На самой Валере рабочие сервисы должны отвечать как раньше: `sudo docker ps`, n8n и документация открываются.

Откат: на Валере `sudo /usr/local/bin/k3s-agent-uninstall.sh`, затем снять добавленные правила ufw (`sudo ufw status numbered` и `sudo ufw delete <номер>`). Рабочие сервисы сервера это не затрагивает.

## Выкат

На `pythagoras`, в корне репозитория:

```sh
for service in core agent-adapter telegram-bot; do
  # Codex ставится только в образ агента.
  extra=""
  [ "$service" = agent-adapter ] && extra="--build-arg CODEX_VERSION=0.154.0"
  docker build -f "$service/Dockerfile" -t "localhost:5000/karandash-$service:latest" $extra . \
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

## Резервные копии

Ночной `CronJob backup` в 03:30 по Москве снимает `pg_dump --format=custom` в том `karandash-backups`
(нода РФ, рядом с базой), проверяет дамп через `pg_restore --list`, удаляет копии старше `KEEP_DAYS`
и сообщает ядру, чем всё кончилось. Из отчёта получаются метрики `karandash_backup_age_seconds`,
`karandash_backup_size_bytes`, `karandash_backup_duration_seconds`, `karandash_backup_ok`
и два алерта: копии нет больше полутора суток и последняя копия не получилась.

Снять копию прямо сейчас:

```sh
kubectl -n karandash create job backup-now --from=cronjob/backup
kubectl -n karandash logs job/backup-now
```

Восстановление в отдельную базу (проверка копии или откат по частям):

```sh
kubectl -n karandash exec -it statefulset/postgres -- psql -U karandash -d postgres \
  -c "create database restore_check"
# файл копии лежит в томе karandash-backups; поднять под с этим томом и выполнить:
pg_restore -h postgres -U karandash -d restore_check --no-owner /backups/karandash_<метка>.dump
```

**Копия уезжает с ноды, только если задан ключ.** Пока секрет `karandash-backup` пуст, а `REMOTE_HOST`
в манифесте не заполнен, копии лежат на том же диске, что и база: это спасает от неудачной миграции
и случайного удаления, но не от потери диска. Чтобы копии уходили на сервер резервных копий,
положите приватный ключ в секрет `karandash-backup` (ключ `backup_key`), а в `ru/backup.yaml`
пропишите `REMOTE_HOST` и `REMOTE_DIR`. Сервер копий должен быть в РФ: в дампе персональные данные.

## Сетевые политики

`components/network-policies` разрешает только нужные входящие соединения: базу — ядру, ядро — приёмщику, агента — ядру. Политику брокера ведёт Дымоход. По умолчанию политики Карандаша выключены: в кластере Дымохода k3s/flannel уже неверно обрабатывал deny-all. Включаются раскомментированием `components` в `kustomization.yaml` после проверки на кластере.
