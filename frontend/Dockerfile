FROM node:22.23.0-bookworm-slim AS dependencies

WORKDIR /application
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci

FROM dependencies AS builder

COPY . .
ARG NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api/v1
ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL}
RUN npm run build

FROM node:22.23.0-bookworm-slim AS runtime

ENV NODE_ENV=production
WORKDIR /application
COPY --from=builder /application ./

USER node
EXPOSE 3001

CMD ["npm", "run", "start", "--", "--host", "0.0.0.0", "--port", "3001"]
