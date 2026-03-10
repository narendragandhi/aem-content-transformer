#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ROOT_DIR}/.env"
EXAMPLE_FILE="${ROOT_DIR}/.env.example"

load_env() {
  if [[ -f "${ENV_FILE}" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "${ENV_FILE}"
    set +a
  elif [[ -f "${EXAMPLE_FILE}" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "${EXAMPLE_FILE}"
    set +a
  fi
}

check_cmd() {
  local name="$1"
  if command -v "${name}" >/dev/null 2>&1; then
    printf "OK  - %s found\n" "${name}"
  else
    printf "WARN- %s not found\n" "${name}"
  fi
}

check_required() {
  local key="$1"
  local value="${!key:-}"
  if [[ -z "${value}" ]]; then
    printf "WARN- %s is not set\n" "${key}"
  else
    printf "OK  - %s=%s\n" "${key}" "${value}"
  fi
}

check_bool() {
  local key="$1"
  local value="${!key:-}"
  if [[ "${value}" == "true" || "${value}" == "false" ]]; then
    printf "OK  - %s=%s\n" "${key}" "${value}"
  else
    printf "WARN- %s should be true|false (current: %s)\n" "${key}" "${value}"
  fi
}

echo "=== Preflight: AEM Content Transformer ==="
load_env

echo
echo "== Tools =="
check_cmd java
check_cmd mvn
check_cmd vault

echo
echo "== Core Paths =="
check_required AEM_OUTPUT_PATH
check_required AEM_SITE_PATH
check_required AEM_TEMPLATE_PATH

echo
echo "== Packaging =="
check_bool AEM_PACKAGE_ZIP
check_bool AEM_FILEVAULT_ENABLED
check_bool AEM_FILEVAULT_VALIDATE
if [[ "${AEM_FILEVAULT_ENABLED:-false}" == "true" ]]; then
  check_required AEM_FILEVAULT_COMMAND
fi

echo
echo "== HTML Policy =="
check_required AEM_HTML_POLICY
if [[ "${AEM_HTML_POLICY:-relaxed}" == "custom" ]]; then
  check_required AEM_HTML_ALLOWED_TAGS
  check_required AEM_HTML_ALLOWED_ATTRS
  check_required AEM_HTML_ALLOWED_PROTOCOLS
fi

echo
echo "== Template Governance =="
check_required AEM_ALLOWED_TEMPLATES
check_required AEM_CLOUDSERVICE_CONFIGS

echo
echo "== Asset Ingestion =="
check_bool AEM_ASSET_DOWNLOAD
check_bool AEM_ASSET_API_ENABLED
if [[ "${AEM_ASSET_API_ENABLED:-false}" == "true" ]]; then
  check_required AEM_ASSET_API_UPLOAD_URL
  check_required AEM_ASSET_API_AUTH_TYPE
  if [[ "${AEM_ASSET_API_AUTH_TYPE:-}" == "basic" ]]; then
    check_required AEM_ASSET_API_AUTH_USER
    check_required AEM_ASSET_API_AUTH_PASSWORD
  elif [[ "${AEM_ASSET_API_AUTH_TYPE:-}" == "bearer" ]]; then
    check_required AEM_ASSET_API_AUTH_TOKEN
  else
    printf "WARN- AEM_ASSET_API_AUTH_TYPE must be basic|bearer\n"
  fi
fi

echo
echo "Preflight complete."
