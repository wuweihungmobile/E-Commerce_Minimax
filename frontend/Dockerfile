# =============================================
# E-Commerce Frontend - Next.js Multi-stage Build
# AISDLC v0.09 - Stage 8 Development Preparation
# =============================================

# Stage 1: Dependencies
FROM node:20-alpine AS deps
WORKDIR /app

# Install dependencies only
COPY package*.json ./
RUN npm ci

# Stage 2: Builder
FROM node:20-alpine AS builder
WORKDIR /app

# Copy cached dependencies from deps stage
COPY --from=deps /app/node_modules ./node_modules
COPY . .

# Disable Next.js telemetry
ENV NEXT_TELEMETRY_DISABLED=1

# Build-time API URL (defaults to localhost for local development)
ARG NEXT_PUBLIC_API_URL=http://localhost:8080/api/v2
ENV NEXT_PUBLIC_API_URL=${NEXT_PUBLIC_API_URL}

# Build the application
RUN npm run build

# Stage 3: Production Runner
FROM node:20-alpine AS runner
WORKDIR /app

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1

# Create non-root user for security
RUN addgroup --system --gid 1001 nodejs
RUN adduser --system --uid 1001 nextjs

# Set ownership
RUN mkdir -p .next/cache && chown -R nextjs:nodejs /app

# Copy built assets
COPY --from=builder /app/public ./public
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static
COPY --from=builder --chown=nextjs:nodejs /app/package.json ./

USER nextjs

EXPOSE 3000

ENV PORT=3000
ENV HOSTNAME="0.0.0.0"

# Use standalone server for production
CMD ["node", "server.js"]
