# Stage 1: Build the React app
FROM node:22-alpine AS builder

# Mirror the repo layout so '../api/openapi.yaml' in package.json scripts resolves correctly
WORKDIR /app/frontend

# openapi-generator-cli requires a JRE at code-generation time
RUN apk add --no-cache openjdk21-jre-headless

# Install dependencies in a separate layer for caching.
# Uses npm install (not npm ci) so the lock file does not need to be committed
# before a Docker build. The CI pipeline uses npm ci with the committed lock file.
COPY frontend/package.json ./
RUN npm install

# Copy the OpenAPI spec (referenced as ../api/openapi.yaml from the frontend workdir)
COPY api/openapi.yaml /app/api/openapi.yaml

# Copy the rest of the frontend source
COPY frontend/ ./

# Generate the API client and produce the production build
RUN npm run build

# Stage 2: Serve with Nginx
FROM nginx:alpine

COPY --from=builder /app/frontend/dist /usr/share/nginx/html
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf

# Grant permissions for rootless execution
RUN chown -R nginx:nginx /usr/share/nginx/html /etc/nginx/conf.d \
    && mkdir -p /var/cache/nginx /var/run /var/log/nginx \
    && chown -R nginx:nginx /var/cache/nginx /var/run /var/log/nginx \
    && chmod -R 777 /var/run /var/cache/nginx /var/log/nginx

# Write the PID to a directory accessible by the nginx user
RUN sed -i 's/^user/#user/' /etc/nginx/nginx.conf \
    && sed -i -e '/^pid/s/.*/pid \/var\/run\/nginx.pid;/' /etc/nginx/nginx.conf

USER nginx

EXPOSE 8080
CMD ["nginx", "-g", "daemon off;"]
