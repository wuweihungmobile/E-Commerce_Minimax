import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: '/api/:path*',
        destination: 'http://ecommerce-backend-dev:8080/api/:path*',
      },
    ];
  },
};

export default nextConfig;
