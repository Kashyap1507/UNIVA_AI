#!/usr/bin/env bash
# Redeploy the backend to Google Cloud Run (run from anywhere: ./Server/deploy.sh)
# Secrets live in Secret Manager in project wizly-ai-2026, so no .env is needed here.
set -euo pipefail
cd "$(dirname "$0")"

PROJECT="${PROJECT:-wizly-ai-2026}"
REGION="${REGION:-us-central1}"
ACCOUNT_FLAG=${GCLOUD_ACCOUNT:+--account=$GCLOUD_ACCOUNT}

gcloud $ACCOUNT_FLAG --project="$PROJECT" run deploy univa-ai-server \
  --source . --region "$REGION" --allow-unauthenticated \
  --memory 2Gi --cpu 1 --no-cpu-throttling \
  --min-instances 0 --max-instances 3 --timeout 300 \
  --set-env-vars '^;^FLASK_ENV=production;CORS_ORIGINS=*;LOG_LEVEL=INFO' \
  --set-secrets SECRET_KEY=SECRET_KEY:latest,JWT_SECRET_KEY=JWT_SECRET_KEY:latest,GOOGLE_GEMINI_API_KEY=GOOGLE_GEMINI_API_KEY:latest \
  --quiet
