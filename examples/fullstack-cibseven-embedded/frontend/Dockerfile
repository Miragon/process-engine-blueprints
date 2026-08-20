# Frontend image: build the SPA with Node, serve it with nginx. The nginx layer also reverse-proxies
# the backend paths (/api, /engine-rest, /camunda, /v3/api-docs) to the `app` service, so the browser
# sees a single origin and no CORS is involved — the same topology the Vite dev proxy gives locally.
# The frontend deliberately stays out of the Gradle build (ADR-0005); this image is built by Docker.

# --- build stage -------------------------------------------------------------
FROM node:24-alpine AS build
WORKDIR /app

# Install against the committed lockfile first for a cacheable dependency layer.
COPY package.json package-lock.json ./
RUN npm ci

# `npm run build` = `tsc --noEmit && vite build`; the orval-generated client is committed, so the
# build needs nothing outside this context. Output lands in ./dist.
COPY . .
RUN npm run build

# --- serve stage -------------------------------------------------------------
FROM nginx:alpine AS serve
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
