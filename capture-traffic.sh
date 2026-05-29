#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_FILE="${PROJECT_ROOT}/network_traffic.pcap"
CONTAINER_NAME="swiftpay-network-tap"

echo "Locating Docker network for compose stack..."
NETWORK_NAME="$(docker network ls --format '{{.Name}}' | grep -E 'swiftpay|Swiftpay|payment-ledger' | head -n 1 || true)"

if [[ -z "${NETWORK_NAME}" ]]; then
  echo "No SwiftPay network found. Start docker compose stack first."
  exit 1
fi

echo "Using network: ${NETWORK_NAME}"
echo "Output file: ${OUT_FILE}"

cleanup() {
  echo
  echo "Stopping network tap and finalizing pcap..."
  docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true
  echo "PCAP capture saved to ${OUT_FILE}"
}

trap cleanup EXIT INT TERM

docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true

docker run --name "${CONTAINER_NAME}" \
  --network "${NETWORK_NAME}" \
  --cap-add NET_ADMIN \
  --cap-add NET_RAW \
  -v "${PROJECT_ROOT}:/captures" \
  --rm \
  nicolaka/netshoot \
  sh -c "tcpdump -i any -nn '(port 9092 or port 5432 or port 6379)' -w /captures/network_traffic.pcap"
