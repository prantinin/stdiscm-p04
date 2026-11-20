FROM node:22-alpine
ENV NODE_ENV=production
WORKDIR /app
COPY view/package.json view/package-lock.json ./view/
WORKDIR /app/view
RUN npm ci --omit=dev
WORKDIR /app
COPY view ./view
COPY proto ./proto
EXPOSE 8080
WORKDIR /app/view
CMD ["node", "server.js"]