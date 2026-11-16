# Imagen de hidrasmart-api para Google Cloud Run.
# Etapa 1: instala todo, compila TypeScript y deja solo las dependencias de producción.
FROM node:24-slim AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY tsconfig.json tsconfig.build.json ./
COPY src ./src
RUN npm run build && npm prune --omit=dev

# Etapa 2: imagen final pequeña, sin código fuente ni herramientas de compilación, con usuario sin privilegios.
FROM node:24-slim
ENV NODE_ENV=production
WORKDIR /app
COPY --from=build /app/package.json ./
COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/dist ./dist
USER node
# Cloud Run indica el puerto en la variable PORT (8080 por defecto)
EXPOSE 8080
CMD ["node", "dist/index.js"]
