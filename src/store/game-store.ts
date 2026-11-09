"use client";

import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";
import { TOTAL_LEVELS } from "@/lib/constants";
import { apiFetch, ApiError, getToken, newIdempotencyKey, setToken } from "@/lib/api";
import type { LevelResult, PlayerInfo, RunState, Settlement } from "@/lib/types";

type LoginVO = {
  token: string;
  expiresIn: number;
  player: PlayerInfo;
};

type GameState = {
  hasHydrated: boolean;
  player: PlayerInfo | null;
  /** 由 player 派生，保留旧字段名，页面少改 */
  studentId: string | null;
  role: "student" | "staff" | null;
  /** 是否已经从服务端拿到过对局状态（用于区分「还没有进度」和「还没查」） */
  runReady: boolean;
  runId: number | null;
  runStatus: string | null;
  unlockedLevel: number;
  totalScore: number;
  levelResults: Partial<Record<number, LevelResult>>;
  collectedItems: string[];
  startedAt: string | null;
  lastCompletionDuration: number | null;

  setHasHydrated: (hydrated: boolean) => void;
  /** 学生只传学号；管理员传学号 + 密码 */
  login: (studentNo: string, password?: string) => Promise<void>;
  logout: () => void;
  /** 从服务端恢复进度（刷新页面、换设备都能续玩） */
  refreshRun: () => Promise<void>;
  /** 放弃当前对局并开新局 */
  restart: () => Promise<void>;
  /** 结算结果写入本地缓存；服务端已经算好，这里只做展示 */
  applySettlement: (level: number, settlement: Settlement) => void;
};

type PersistedState = Pick<
  GameState,
  "player" | "studentId" | "role" | "lastCompletionDuration"
>;

const initialState = {
  hasHydrated: false,
  player: null,
  studentId: null,
  role: null,
  runReady: false,
  runId: null,
  runStatus: null,
  unlockedLevel: 1,
  totalScore: 0,
  levelResults: {},
  collectedItems: [],
  startedAt: null,
  lastCompletionDuration: null,
} satisfies Omit<
  GameState,
  | "setHasHydrated"
  | "login"
  | "logout"
  | "refreshRun"
  | "restart"
  | "applySettlement"
>;

const clampLevel = (level: number) => Math.min(TOTAL_LEVELS, Math.max(1, level));

function toLevelResults(run: RunState): Partial<Record<number, LevelResult>> {
  const result: Partial<Record<number, LevelResult>> = {};
  for (const progress of run.levels) {
    result[progress.level] = {
      score: progress.score,
      attempts: progress.attempts,
      questionPassed: progress.questionPassed,
      qrPassed: progress.qrPassed,
      completedAt: run.finishedAt ?? run.startedAt,
    };
  }
  return result;
}

export const useGameStore = create<GameState>()(
  persist(
    (set, get) => ({
      ...initialState,

      setHasHydrated: (hydrated) => set({ hasHydrated: hydrated }),

      login: async (studentNo, password) => {
        // 有密码走普通登录（管理员）；没有密码走学号直登（学生）
        const data = password
          ? await apiFetch<LoginVO>("/api/auth/login", {
              method: "POST",
              body: { studentNo, password },
            })
          : await apiFetch<LoginVO>("/api/auth/student-login", {
              method: "POST",
              body: { studentNo },
            });
        setToken(data.token);

        const isStaff = data.player.role === "STAFF" || data.player.role === "ADMIN";
        set({
          player: data.player,
          studentId: data.player.studentNo,
          role: isStaff ? "staff" : "student",
          unlockedLevel: 1,
          totalScore: 0,
          levelResults: {},
          collectedItems: [],
          runId: null,
          runStatus: null,
          lastCompletionDuration: null,
        });

        // 登录即开局：服务端已有进行中的对局会直接返回，不会重复创建
        const run = await apiFetch<RunState>("/api/runs", {
          method: "POST",
          headers: { "Idempotency-Key": newIdempotencyKey() },
        });
        hydrateFromRun(set, run);
      },

      logout: () => {
        setToken(null);
        set({ ...initialState, hasHydrated: true });
      },

      refreshRun: async () => {
        if (!getToken()) {
          // 本地有玩家缓存但没有 token（过期或手动清除）→ 退回登录页
          get().logout();
          return;
        }
        try {
          const run = await apiFetch<RunState | null>("/api/runs/current");
          if (run) {
            hydrateFromRun(set, run);
          } else {
            set({ runReady: true, runId: null, runStatus: null });
          }
        } catch (err) {
          if (err instanceof ApiError && err.httpStatus === 401) {
            // token 过期：直接退回登录页，避免拿着过期凭证继续操作
            get().logout();
            return;
          }
          // 网络抖动：保留本地缓存，runReady 置真避免页面一直空白
          set({ runReady: true });
        }
      },

      restart: async () => {
        const runId = get().runId;
        if (runId) {
          try {
            await apiFetch(`/api/runs/${runId}/abandon`, { method: "POST" });
          } catch {
            // 已完成的局无法放弃，直接开新局
          }
        }
        const run = await apiFetch<RunState>("/api/runs", {
          method: "POST",
          headers: { "Idempotency-Key": newIdempotencyKey() },
        });
        set({
          totalScore: 0,
          levelResults: {},
          collectedItems: [],
          lastCompletionDuration: null,
        });
        hydrateFromRun(set, run);
      },

      applySettlement: (level, settlement) =>
        set((state) => {
          const nextResults: Partial<Record<number, LevelResult>> = {
            ...state.levelResults,
            [level]: {
              score: settlement.levelScore,
              attempts: state.levelResults[level]?.attempts ?? 1,
              questionPassed: true,
              qrPassed: true,
              completedAt: new Date().toISOString(),
            },
          };

          return {
            levelResults: nextResults,
            totalScore: settlement.totalScore,
            unlockedLevel: settlement.nextLevelNo ?? TOTAL_LEVELS,
            collectedItems: Array.from(
              new Set([...state.collectedItems, ...settlement.collectedItems])
            ),
            lastCompletionDuration:
              settlement.durationSeconds ?? state.lastCompletionDuration,
            runStatus: settlement.runCompleted ? "COMPLETED" : state.runStatus,
          };
        }),
    }),
    {
      name: "lib-hunt-game-state-v4",
      version: 4,
      storage: createJSONStorage(() => localStorage),
      onRehydrateStorage: () => (state) => {
        state?.setHasHydrated(true);
      },
      partialize: (state): PersistedState => ({
        player: state.player,
        studentId: state.studentId,
        role: state.role,
        lastCompletionDuration: state.lastCompletionDuration,
      }),
    }
  )
);

function hydrateFromRun(
  set: (partial: Partial<GameState>) => void,
  run: RunState
) {
  const completed = run.status === "COMPLETED";
  set({
    runReady: true,
    runId: run.runId,
    runStatus: run.status,
    unlockedLevel: completed
      ? TOTAL_LEVELS
      : clampLevel(run.lastLevelNo ?? 1),
    totalScore: run.totalScore ?? 0,
    levelResults: toLevelResults(run),
    collectedItems: run.items ?? [],
    startedAt: run.startedAt,
    lastCompletionDuration: run.durationSeconds ?? null,
  });
}
