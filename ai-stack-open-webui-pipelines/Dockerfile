FROM ghcr.io/open-webui/pipelines:git-b955a8f-cuda

ENV CUSTOM_PIPELINE_DIR=pipelines-custom

RUN pip install \
  --upgrade --no-deps --force-reinstall \
  --index-url https://download.pytorch.org/whl/cu126 \
  torch==2.6.0+cu126

# Copy Custom Pipelines
COPY ./ai-pipelines-runtime-data /app/${CUSTOM_PIPELINE_DIR}

# Install pipelines
RUN set -eux; \
    PIPELINE_PREFIX="file:///app/${CUSTOM_PIPELINE_DIR}"; \
    URLS=""; \
    for f in "${CUSTOM_PIPELINE_DIR}"/*.py; do \
      name="${f##*/}"; \
      URLS="${URLS:+$URLS;}$PIPELINE_PREFIX/$name"; \
    done; \
    echo "[PIPELINES INSTALLATION] New Custom Install Pipes: $URLS"; \
    PIPELINES_URLS="$URLS" ./start.sh --mode setup; \
    echo "[PIPELINES INSTALLATION FINISHED]."
