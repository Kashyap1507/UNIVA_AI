#!/usr/bin/env bash
# Show the latest sign-in codes from the Cloud Run server (no email service is configured,
# so codes are written to the server log). Usage: ./Server/login-codes.sh [minutes, default 15]
set -euo pipefail
MINUTES="${1:-15}"
ACCOUNT_FLAG=${GCLOUD_ACCOUNT:+--account=$GCLOUD_ACCOUNT}

gcloud $ACCOUNT_FLAG --project="${PROJECT:-wizly-ai-2026}" logging read \
  'resource.type="cloud_run_revision" AND resource.labels.service_name="univa-ai-server" AND textPayload:"Mock verification email"' \
  --freshness="${MINUTES}m" --limit=10 --format="value(timestamp,textPayload)"
