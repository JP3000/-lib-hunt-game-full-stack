// ============================================================
// 与服务端 DTO 对应的类型
// 注意：答案（正确选项、二维码内容）永远不会出现在这些类型里
// ============================================================

export type LevelOption = {
  id: string;
  label: string;
};

export type ItemConfig = {
  id: string;
  name: string;
  imageUrl: string;
  description: string;
};

/** GET /api/levels/{level} 的返回，字段与后端 LevelDetailVO 一致 */
export type LevelConfig = {
  level: number;
  title: string;
  story: string;
  /** 過關情節：答題正確後展示 */
  completionStory?: string;
  question: string;
  options: LevelOption[];
  /** 本題是否多選（只告訴「有幾個正確答案」，不告訴是哪幾個） */
  multiple?: boolean;
  /** 二维码藏匿地点提示（不是答案） */
  qrLocation?: string;
  qrLocationNote?: string;
  videoUrl?: string;
  storyImageUrl?: string;
  storyImageAlt?: string;
  storyImageCaption?: string;
  items: ItemConfig[];
};

/** GET /api/levels 的返回 */
export type LevelSummary = {
  level: number;
  title: string;
  items: ItemConfig[];
};

export type LevelResult = {
  score: number;
  attempts: number;
  questionPassed: boolean;
  qrPassed: boolean;
  completedAt: string;
};

export type PlayerInfo = {
  id: number;
  studentNo: string;
  nickname: string | null;
  role: string;
  status: number;
};

export type LevelProgress = {
  level: number;
  score: number;
  attempts: number;
  questionPassed: boolean;
  qrPassed: boolean;
};

/** GET/POST /api/runs 的返回 */
export type RunState = {
  runId: number;
  status: "IN_PROGRESS" | "COMPLETED" | "ABANDONED" | string;
  startedAt: string;
  finishedAt: string | null;
  totalScore: number;
  durationSeconds: number | null;
  lastLevelNo: number;
  levels: LevelProgress[];
  items: string[];
};

export type Settlement = {
  levelScore: number;
  totalScore: number;
  nextLevelNo: number | null;
  runCompleted: boolean;
  durationSeconds: number | null;
  collectedItems: string[];
};

export type AnswerResult = {
  /** 本次提交是否通过本题（少选拿到部分分也算通过） */
  correct: boolean;
  attempts: number;
  /** 本题得分：全对 20，少选按正确选项比例给分，错选 0 */
  score: number;
  questionPassed: boolean;
  qrPassed: boolean;
  nextAction: "RETRY" | "SCAN_QR" | "NEXT";
  settlement: Settlement | null;
};

export type QrResult = {
  correct: boolean;
  questionPassed: boolean;
  qrPassed: boolean;
  settlement: Settlement | null;
};

export type LeaderboardEntry = {
  rank: number;
  nickname: string;
  totalScore: number;
  durationSeconds: number | null;
};

/** GET /api/admin/stats（仅 ADMIN） */
export type AdminStats = {
  playerCount: number;
  totalCompletions: number;
  avgScore: number;
  avgDuration: number;
};
