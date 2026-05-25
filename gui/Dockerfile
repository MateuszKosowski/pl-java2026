# Stage 1: build the static SPA
FROM node:22-alpine AS builder
WORKDIR /app

# Lockfile is not committed (project convention); the glob keeps the COPY valid
# whether or not one is present locally.
COPY package.json package-lock.json* ./
RUN npm install

COPY . .
RUN npm run build

# Stage 2: serve the static build with nginx, proxying API/auth calls to the backend
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/build /usr/share/nginx/html

EXPOSE 80
