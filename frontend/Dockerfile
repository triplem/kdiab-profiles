# Stage 1: Build the frontend
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
RUN npm ci
COPY . .
# We don't need to bake API_URL if we use relative paths / proxy
RUN npm run build

# Stage 2: Serve with Nginx
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Grant permissions to the nginx user for rootless execution
RUN chown -R nginx:nginx /usr/share/nginx/html /etc/nginx/conf.d \
    && mkdir -p /var/cache/nginx /var/run /var/log/nginx \
    && chown -R nginx:nginx /var/cache/nginx /var/run /var/log/nginx \
    && chmod -R 777 /var/run /var/cache/nginx /var/log/nginx

# Update the nginx configuration to write the PID to a directory accessible by the nginx user
RUN sed -i 's/^user/#user/' /etc/nginx/nginx.conf \
    && sed -i -e '/^pid/s/.*/pid \/var\/run\/nginx.pid;/' /etc/nginx/nginx.conf

USER nginx

EXPOSE 8080
CMD ["nginx", "-g", "daemon off;"]
