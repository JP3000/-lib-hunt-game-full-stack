/**
 * 后端 API 客户端。
 *
 * 所有请求都走同源的 `/api/*`：
 *  - 开发环境由 next.config.ts 的 rewrite 转发到 Spring Boot (8080)
 *  - 生产环境由 Nginx 把 `/api/` 反代到 Spring Boot
 * 这样浏览器不需要跨域，也不需要在前端硬编码后端地址。
 */

const TOKEN_KEY = "libhunt.token";

export type ApiEnvelope<T> = {
  code: number;
  msg: string;
  data: T;
};

export class ApiError extends Error {
  readonly code: number;
  readonly httpStatus: number;

  constructor(code: number, message: string, httpStatus: number) {
    super(message);
    this.name = "ApiError";
    this.code = code;
    this.httpStatus = httpStatus;
  }
}

export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string | null): void {
  if (typeof window === "undefined") return;
  if (token) {
    window.localStorage.setItem(TOKEN_KEY, token);
  } else {
    window.localStorage.removeItem(TOKEN_KEY);
  }
}

type RequestOptions = {
  method?: "GET" | "POST" | "PATCH" | "PUT" | "DELETE";
  body?: unknown;
  headers?: Record<string, string>;
};

export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...options.headers,
  };

  const token = getToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(path, {
    method: options.method ?? "GET",
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
    cache: "no-store",
  });

  let payload: ApiEnvelope<T> | null = null;
  try {
    payload = (await response.json()) as ApiEnvelope<T>;
  } catch {
    // 204 或网关错误时没有 JSON 体
  }

  if (!response.ok || !payload || payload.code !== 0) {
    if (response.status === 401) {
      setToken(null);
    }
    throw new ApiError(
      payload?.code ?? response.status,
      payload?.msg ?? `请求失败（HTTP ${response.status}）`,
      response.status
    );
  }

  return payload.data;
}

export function newIdempotencyKey(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }
  return `run-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}
