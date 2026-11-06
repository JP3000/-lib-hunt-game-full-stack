import type { NextConfig } from "next";

/**
 * 本地开发时，把 /api/* 转发到 Spring Boot。
 * 生产环境由 Nginx 做同样的转发，前端始终只请求同源 /api。
 */
const API_ORIGIN = process.env.API_PROXY_TARGET ?? "http://127.0.0.1:8080";

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${API_ORIGIN}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
