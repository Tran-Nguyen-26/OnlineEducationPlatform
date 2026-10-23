# Multi-stage Dockerfile for OEP Frontend React SPA
# Security & Sizing Compliance:
# - Base image: nginxinc/nginx-unprivileged:alpine (Image size ~25MB <= 60MB limit)
# - Runs as non-root user (nginx, UID 101)
# - Hardened with strict security headers and healthz endpoint

# Stage 1: Build React SPA
FROM node:22-alpine AS builder

WORKDIR /app

# Copy package descriptors for layer caching
COPY package.json package-lock.json* ./

# Install dependencies cleanly
RUN npm ci || npm install

# Copy source code and config files
COPY . .

# Build production bundle
RUN npm run build

# Stage 2: Minimal, secure unprivileged Nginx runtime
FROM nginxinc/nginx-unprivileged:alpine AS runner

# Copy customized Nginx configuration
COPY --chown=nginx:nginx nginx.conf /etc/nginx/conf.d/default.conf

# Copy compiled static assets from builder
COPY --from=builder --chown=nginx:nginx /app/dist /usr/share/nginx/html

# Expose unprivileged port
EXPOSE 8080

# Health check endpoint for ALB target group
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -q --spider http://localhost:8080/healthz || exit 1

# Start nginx daemon in foreground
CMD ["nginx", "-g", "daemon off;"]
