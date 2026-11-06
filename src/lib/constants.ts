/** 更新後手動遞增，強制瀏覽器重新載入靜態資源 */
export const CACHE_BUSTER = "20250710";

export const TOTAL_LEVELS = 12;
/**
 * 12 关满分：每关首答正确 100 分（服务端 GameRules.LEVEL_BASE_SCORE × 12）。
 * 用于管理端看板的「正确率」换算。
 */
export const TOTAL_MAX_SCORE = 1200;

export const STUDENT_ID_REGEX = /^[A-Za-z]{2}\d{6}$/;
export const FAST_PASS_STUDENT_ID = "ab999999";

/** 職員用戶名長度限制 */
export const STAFF_USERNAME_MIN_LENGTH = 5;
export const STAFF_USERNAME_MAX_LENGTH = 15;
/** 是否為合法的職員用戶名（僅檢查長度） */
export const isValidStaffUsername = (value: string) => {
  const trimmed = value.trim();
  return trimmed.length >= STAFF_USERNAME_MIN_LENGTH && trimmed.length <= STAFF_USERNAME_MAX_LENGTH;
};

// ============================================================
// 影片 URL 常數 — 對應 AI 媒體素材製作方案中的 5 支劇情影片
// ============================================================

/** 序幕：字條的降臨 — APP 開場動畫 / Level 1 前置劇情 */
export const PROLOGUE_VIDEO_URL = `/video/prologue-note.mp4?v=${CACHE_BUSTER}`;

/** Level 1：初臨密境 */
export const LEVEL1_VIDEO_URL = `/video/level1.mp4?v=${CACHE_BUSTER}`;

/** Level 4：福松丸的真相 — 核心劇情轉折 */
export const LEVEL4_VIDEO_URL = `/video/level4-fushong.mp4?v=${CACHE_BUSTER}`;

/** Level 7：銀幣的墜落 — 從地圖畫框後掉出 */
export const LEVEL7_VIDEO_URL = `/video/level7-coin.mp4?v=${CACHE_BUSTER}`;

/** Level 9：鎮館之寶 — 古籍展櫃 + 利瑪竇徐光啟閃回 */
export const LEVEL9_VIDEO_URL = `/video/level9-treasure.mp4?v=${CACHE_BUSTER}`;

/** 終章：五德歸一 — 五角形拼合 + 圖書館全景 */
export const FINALE_VIDEO_URL = `/video/finale-pentagon.mp4?v=${CACHE_BUSTER}`;