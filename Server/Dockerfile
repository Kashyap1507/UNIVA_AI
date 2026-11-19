# Univa AI backend image for Google Cloud Run
FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    FLASK_ENV=production \
    PORT=8080

WORKDIR /app

# tesseract + poppler: OCR and PDF-to-image for course materials and scans
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        build-essential tesseract-ocr poppler-utils curl \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir --upgrade pip \
    && pip install --no-cache-dir -r requirements.txt

COPY . .

RUN useradd --create-home app && mkdir -p uploads/general && chown -R app:app /app
USER app

EXPOSE 8080

# One worker keeps the in-memory cache and background ingestion threads in one process;
# threads handle concurrent requests. Long timeout for AI calls.
CMD exec gunicorn --bind 0.0.0.0:${PORT} --workers 1 --threads 8 --timeout 300 main:app
