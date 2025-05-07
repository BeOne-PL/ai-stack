#!/bin/sh

# Write dynamic configuration to config.js
mkdir -p /usr/share/nginx/html/basic-panel/assets
echo "window.__APP_CONFIG__ = { \
  chatServer: '${CHAT_SERVICE_SERVER}' \
};" > /usr/share/nginx/html/basic-panel/assets/config.js

# Start NGINX
exec "$@"

