# syntax=docker/dockerfile:1.7
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . .
# Empty = same-origin API (nginx proxies /api to the gateway). Set to a full URL only if the API lives on another host.
ARG VITE_API_BASE_URL=
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
RUN npm run build

FROM nginx:1.27-alpine
# nginx renders /etc/nginx/templates/*.template with the container environment on start
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
ENV GATEWAY_URL=http://api-gateway:8080 \
    STORAGE_PUBLIC_ORIGIN=http://localhost:9000
# only substitute our own variables (nginx's $uri, $host ... must stay untouched)
ENV NGINX_ENVSUBST_FILTER=^(GATEWAY_URL|STORAGE_PUBLIC_ORIGIN)$
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=3s CMD wget -q -O /dev/null http://127.0.0.1/healthz || exit 1
