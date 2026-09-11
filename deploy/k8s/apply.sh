#!/bin/sh
# Выкат Карандаша в k3s Дымохода. Запускать на pythagoras из корня репозитория: sh deploy/k8s/apply.sh
# Сначала скрипт убеждается, что kubectl смотрит именно в кластер Дымохода, и только потом что-то пишет.
set -eu

DIR=$(cd "$(dirname "$0")" && pwd)
NAMESPACE=karandash

echo "Контекст kubectl: $(kubectl config current-context 2>/dev/null || echo '<не задан>')"

# 1. Это кластер Дымохода: ноды с метками Карандаша есть только там, в любом другом кластере — стоп.
ru=$(kubectl get nodes -l karandash/region=ru -o name)
foreign=$(kubectl get nodes -l karandash/region=foreign -o name)
if [ -z "$ru" ] || [ -z "$foreign" ]; then
  echo "Стоп: в этом кластере нет нод с метками karandash/region=ru и karandash/region=foreign." >&2
  echo "Проверьте контекст kubectl. Метки ставятся один раз — deploy/k8s/README.md, «Метки нод»." >&2
  exit 1
fi
echo "Нода РФ: $ru, зарубежная нода: $foreign"

# 2. Трафик между нодами должен быть зашифрован: бот с настоящим токеном сразу обслуживает живых людей.
#    Пробный выкат без шифрования — только по явному разрешению.
backends=$(kubectl get nodes -o jsonpath='{range .items[*]}{.metadata.annotations.flannel\.alpha\.coreos\.com/backend-type}{" "}{end}')
case "$backends" in
  *vxlan*|*host-gw*)
    if [ "${KARANDASH_ALLOW_PLAINTEXT_INTERNODE:-}" != "1" ]; then
      echo "Стоп: flannel без шифрования ($backends) — текст, фото и токены пошли бы между странами открыто." >&2
      echo "Сначала WireGuard или TLS (README, «Перед боевым выкатом»)." >&2
      echo "Пробный выкат без пользователей: KARANDASH_ALLOW_PLAINTEXT_INTERNODE=1 sh deploy/k8s/apply.sh" >&2
      exit 1
    fi
    echo "ВНИМАНИЕ: flannel без шифрования ($backends), выкат по явному разрешению — с настоящими пользователями не запускать." >&2
    ;;
  *wireguard*)
    echo "flannel: $backends"
    ;;
  *)
    echo "ВНИМАНИЕ: бэкенд flannel не определён ('$backends') — проверьте шифрование вручную (README)." >&2
    ;;
esac

# 3. Секреты заводятся руками вне git; без них поды не стартуют.
kubectl apply -f "$DIR/namespace.yaml"
missing=""
for secret in karandash-db karandash-core karandash-agent karandash-bot; do
  kubectl -n "$NAMESPACE" get secret "$secret" >/dev/null 2>&1 || missing="$missing $secret"
done
if [ -n "$missing" ]; then
  echo "Стоп: не созданы секреты:$missing." >&2
  echo "Заполните копию deploy/k8s/secrets.example.yaml вне git и выполните kubectl apply -f <файл>." >&2
  exit 1
fi

# 4. Манифесты.
kubectl apply -k "$DIR"
kubectl -n "$NAMESPACE" get pods -o wide
