# Serve with Nginx
FROM nginx:alpine

# Copy the static assets from the host (built by CI)
# e.g., npm run build
ARG DIST_PATH=frontend/dist
COPY ${DIST_PATH} /usr/share/nginx/html
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf

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
