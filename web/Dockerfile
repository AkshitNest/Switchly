FROM node:24-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:24-alpine
WORKDIR /app
COPY --from=build /app/dist ./dist
COPY server ./server
ENV HOST=0.0.0.0 PORT=3000
USER node
EXPOSE 3000
CMD ["node","server/index.mjs"]
