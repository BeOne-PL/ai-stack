FROM node:20.9.0-bookworm AS build
WORKDIR /app
#COPY --from=cache --chown=node:node /app/node_modules /app/node_modules
COPY . .
RUN npm install --save-exact --legacy-peer-deps
RUN node --max_old_space_size=8192 node_modules/@angular/cli/bin/ng build --base-href /basic-panel/ --configuration production

FROM nginx:stable-alpine3.20-perl AS final
WORKDIR /usr/share/nginx/html

COPY ./entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

COPY --from=build /app/dist ./basic-panel
COPY nginx.conf /etc/nginx/nginx.conf

# Set default values for environment variables
ENV CHAT_SERVICE_SERVER=http://localhost:80

ENTRYPOINT ["/entrypoint.sh"]
CMD ["nginx", "-g", "daemon off;"]
