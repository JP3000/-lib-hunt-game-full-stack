import { DEFAULT_LOCALE, type Locale } from "@/lib/i18n";

type Translations = {
  brand: string;
  gameName: string;
  metaDescription: string;
  login: {
    headline: string;
    description: string;
    adminDescription: string;
    staffDescription: string;
    studentIdLabel: string;
    studentIdPlaceholder: string;
    passwordLabel: string;
    passwordPlaceholder: string;
    submit: string;
    loading: string;
    invalidStudentId: string;
    passwordRequired: string;
    adminRole: string;
    roleLabel: string;
    studentRole: string;
    staffRole: string;
    staffUsernameLabel: string;
    staffUsernamePlaceholder: string;
    invalidStaffUsername: string;
  };
  intro: {
    eyebrow: string;
    headline: string;
    subtitle: string;
    start: string;
  };
  header: {
    player: string;
    score: string;
    items: string;
    map: string;
    leaderboard: string;
    logout: string;
    language: string;
  };
  footer: {
    producer: string;
    disclaimer: string;
  };
  map: {
    title: string;
    subtitle: (data: { completed: number; total: number; score: number }) => string;
    routeTitle: string;
    viewLeaderboard: string;
    completed: string;
    available: string;
    locked: string;
    unlockHint: string;
    stamped: string;
  };
  level: {
    title: (level: number) => string;
    storyHeading: string;
    questionHeading: string;
    multiSelectHint: string;
    submitAnswer: string;
    answerCorrect: (score: number) => string;
    submitError: string;
    completedHint: string;
    settlementHeading: string;
    questionStatus: string;
    qrStatus: string;
    completeAndNext: string;
    backToMap: string;
    completionStoryHeading: string;
    itemUnlockHint: string;
    loading: string;
    loadError: string;
    selectFirst: string;
    runNotReady: string;
    attemptsLabel: (attempts: number) => string;
  };
  qr: {
    heading: string;
    locationLabel: string;
    description: string;
    scanArea: string;
    startScan: string;
    stopScan: string;
    manualPlaceholder: string;
    manualVerify: string;

    emptyValue: string;
    success: string;
    mismatch: string;
    cameraError: string;
  };
  items: {
    panelTitle: string;
    panelSubtitle: string;
    close: string;
    locked: string;
  };
  leaderboard: {
    title: string;
    subtitleComplete: string;
    subtitleIncomplete: string;
    finalScore: string;
    progress: (data: { completed: number; total: number }) => string;
    backToMap: string;
    reset: string;
    levelBreakdown: string;
    notCompleted: string;
    itemSection: string;
    scoreUnit: string;
    rank: string;
    player: string;
    duration: string;
    empty: string;
    loading: string;
  };
  authGate: {
    loading: string;
  };
  result: {
    title: string;
    headline: string;
    subtitle: string;
    score: string;
    duration: string;
    items: string;
    itemWall: string;
    viewLeaderboard: string;
    backToMap: string;
    completedAt: string;
    screenshotHint: string;
  };
};

const translations: Record<Locale, Translations> = {
  "zh-Hant": {
    brand: "鄭芝龍的寶藏 II",
    gameName: "鄭芝龍的寶藏 II",
    metaDescription: "一張泛黃字條，一首明代古詩，一段橫跨四百年的圖書館尋寶之旅。",
    login: {
      headline: "鄭芝龍的寶藏 II",
      description: "輸入學號（2 個字母 + 6 個數字）即可開始尋寶。\n示例: ab565666",
      adminDescription: "管理員請使用賬號與密碼登入。",
      staffDescription: "輸入職員用戶名即可開始尋寶。\n用戶名長度：5 至 15 個字符。",
      studentIdLabel: "帳號 / 學號",
      studentIdPlaceholder: "輸入學號",
      passwordLabel: "密碼",
      passwordPlaceholder: "輸入密碼",
      submit: "開始解碼",
      loading: "正在喚醒解碼系統...",
      invalidStudentId: "學號格式：2 個字母 + 6 個數字（如 ab565666）。",
      passwordRequired: "請輸入密碼。",
      adminRole: "管理員",
      roleLabel: "登入身分",
      studentRole: "學生",
      staffRole: "職員",
      staffUsernameLabel: "用戶名",
      staffUsernamePlaceholder: "輸入用戶名（5-15 個字符）",
      invalidStaffUsername: "用戶名長度需為 5 至 15 個字符。",
    },
    intro: {
      eyebrow: "序幕 · 神祕字條",
      headline: "鄭芝龍的寶藏 II",
      subtitle: "一張泛黃的字條，一首四百年前的詩。簽名 N. Iquan, 1634——他是誰？寶藏又在哪裡？",
      start: "開始尋寶",
    },
    header: {
      player: "玩家",
      score: "總分",
      items: "道具",
      map: "返回地圖",
      leaderboard: "積分板",
      logout: "重新登入",
      language: "語言",
    },
    footer: {
      producer: "澳門大學圖書館 製作",
      disclaimer: "本故事純屬虛構，如有雷同實屬巧合。",
    },
    map: {
      title: "尋寶線索圖",
      subtitle: ({ completed, total, score }) => `已完成 ${completed}/${total} 關 · 當前總分 ${score}`,
      routeTitle: "尋寶路線",
      viewLeaderboard: "查看積分板",
      completed: "已完成",

      available: "可挑戰",
      locked: "未解鎖",
      unlockHint: "先完成前置關卡",
      stamped: "已蓋章",
    },
    level: {
      title: (level) => `第 ${level} 關`,
      storyHeading: "劇情線索",
      questionHeading: "問題挑戰",
      multiSelectHint: "本題可多選",
      submitAnswer: "提交答案",
      answerCorrect: (score) => `本題得 ${score} 分`,
      submitError: "提交失敗，請稍後再試。",
      completedHint: "本關已完成，可繼續下一關或返回地圖。",
      settlementHeading: "本關結算",
      questionStatus: "答題狀態",
      qrStatus: "掃碼狀態",
      completeAndNext: "完成本關並繼續",
      backToMap: "返回地圖",
      completionStoryHeading: "過關情節",
      itemUnlockHint: "通關後自動收錄到道具欄",
      loading: "正在載入關卡線索...",
      loadError: "關卡載入失敗，請稍後再試。",
      selectFirst: "請先選擇答案再提交。",
      runNotReady: "對局尚未就緒，請返回地圖後重試。",
      attemptsLabel: (attempts) => `已嘗試 ${attempts} 次`,
    },
    qr: {
      heading: "前往指定地點掃碼驗證",
      locationLabel: "位置",
      description: "可選擇呼叫攝影鏡頭掃碼，也可手動輸入二維碼內容進行驗證。",
      scanArea: "掃碼區域",
      startScan: "開始掃碼",
      stopScan: "停止掃碼",
      manualPlaceholder: "手動輸入二維碼內容",
      manualVerify: "手動驗證",

      emptyValue: "請輸入二維碼內容後再驗證。",
      success: "驗證成功，寶藏機關已解鎖。",
      mismatch: "二維碼內容不匹配，請重試。",
      cameraError: "無法打開攝影鏡頭，請改用手動輸入二維碼內容。",
    },
    items: {
      panelTitle: "已獲得的道具",
      panelSubtitle: "道具收藏",
      close: "關閉",
      locked: "未解鎖",
    },
    leaderboard: {
      title: "積分板總覽",
      subtitleComplete: "恭喜完成全部關卡",
      subtitleIncomplete: "繼續挑戰以解鎖完整積分板",
      finalScore: "最終得分",
      progress: ({ completed, total }) => `完成進度: ${completed}/${total}`,
      backToMap: "返回地圖",
      reset: "重置進度",
      levelBreakdown: "關卡明細",
      notCompleted: "未完成",
      itemSection: "已獲得道具",
      scoreUnit: "分",
      rank: "排名",
      player: "玩家",
      duration: "耗時",
      empty: "暫無通關記錄",
      loading: "正在載入積分板...",
    },
    authGate: {
      loading: "正在載入探險檔案...",
    },
    result: {
      title: "尋寶結果",
      headline: "恭喜完成全部關卡！",
      subtitle: "你已成功破解鄭芝龍的寶藏之謎",
      score: "總分",
      duration: "耗時",
      items: "道具",
      itemWall: "道具收藏",
      viewLeaderboard: "查看積分板",
      backToMap: "返回地圖",
      completedAt: "通關日期",
      screenshotHint: "截圖保存這份通關記錄",
    },
  },
  "zh-Hans": {
    brand: "郑芝龙的宝藏 II",
    gameName: "郑芝龙的宝藏 II",
    metaDescription: "一张泛黄字条，一首明代古诗，一段横跨四百年的图书馆寻宝之旅。",
    login: {
      headline: "郑芝龙的宝藏 II",
      description: "输入学号（2 个字母 + 6 个数字）即可开始寻宝。\n示例: ab565666",
      adminDescription: "管理员请使用账号与密码登录。",
      staffDescription: "输入职员用户名即可开始寻宝。\n用户名长度：5 至 15 个字符。",
      studentIdLabel: "账号 / 学号",
      studentIdPlaceholder: "输入学号",
      passwordLabel: "密码",
      passwordPlaceholder: "输入密码",
      submit: "开始解码",
      loading: "正在唤醒解码系统...",
      invalidStudentId: "学号格式：2 个字母 + 6 个数字（如 ab565666）。",
      passwordRequired: "请输入密码。",
      adminRole: "管理员",
      roleLabel: "登录身份",
      studentRole: "学生",
      staffRole: "职员",
      staffUsernameLabel: "用户名",
      staffUsernamePlaceholder: "输入用户名（5-15 个字符）",
      invalidStaffUsername: "用户名长度需为 5 至 15 个字符。",
    },
    intro: {
      eyebrow: "序幕 · 神秘字条",
      headline: "郑芝龙的宝藏 II",
      subtitle: "一张泛黄的字条，一首四百年前的诗。签名 N. Iquan, 1634——他是谁？宝藏又在哪里？",
      start: "开始寻宝",
    },
    header: {
      player: "玩家",
      score: "总分",
      items: "道具",
      map: "返回地图",
      leaderboard: "积分板",
      logout: "重新登录",
      language: "语言",
    },
    footer: {
      producer: "澳门大学图书馆 制作",
      disclaimer: "本故事纯属虚构，如有雷同实属巧合。",
    },
    map: {
      title: "寻宝线索图",
      subtitle: ({ completed, total, score }) => `已完成 ${completed}/${total} 关 · 当前总分 ${score}`,
      routeTitle: "寻宝路线",
      viewLeaderboard: "查看积分板",
      completed: "已完成",

      available: "可挑战",
      locked: "未解锁",
      unlockHint: "先完成前置关卡",
      stamped: "已盖章",
    },
    level: {
      title: (level) => `第 ${level} 关`,
      storyHeading: "剧情线索",
      questionHeading: "问题挑战",
      multiSelectHint: "本题可多选",
      submitAnswer: "提交答案",
      answerCorrect: (score) => `本题得 ${score} 分`,
      submitError: "提交失败，请稍后再试。",
      completedHint: "本关已完成，可继续下一关或返回地图。",
      settlementHeading: "本关结算",
      questionStatus: "答题状态",
      qrStatus: "扫码状态",
      completeAndNext: "完成本关并继续",
      backToMap: "返回地图",
      completionStoryHeading: "过关情节",
      itemUnlockHint: "通关后自动收录到道具栏",
      loading: "正在加载关卡线索...",
      loadError: "关卡加载失败，请稍后再试。",
      selectFirst: "请先选择答案再提交。",
      runNotReady: "对局尚未就绪，请返回地图后重试。",
      attemptsLabel: (attempts) => `已尝试 ${attempts} 次`,
    },
    qr: {
      heading: "前往指定地点扫码验证",
      locationLabel: "位置",
      description: "可选择呼叫摄影镜头扫码，也可手动输入二维码内容进行验证。",
      scanArea: "扫码区域",
      startScan: "开始扫码",
      stopScan: "停止扫码",
      manualPlaceholder: "手动输入二维码内容",
      manualVerify: "手动验证",

      emptyValue: "请输入二维码内容后再验证。",
      success: "验证成功，宝藏机关已解锁。",
      mismatch: "二维码内容不匹配，请重试。",
      cameraError: "无法打开摄影镜头，请改用手动输入二维码内容。",
    },
    items: {
      panelTitle: "已获得的道具",
      panelSubtitle: "道具收藏",
      close: "关闭",
      locked: "未解锁",
    },
    leaderboard: {
      title: "积分板总览",
      subtitleComplete: "恭喜完成全部关卡",
      subtitleIncomplete: "继续挑战以解锁完整积分板",
      finalScore: "最终得分",
      progress: ({ completed, total }) => `完成进度: ${completed}/${total}`,
      backToMap: "返回地图",
      reset: "重置进度",
      levelBreakdown: "关卡明细",
      notCompleted: "未完成",
      itemSection: "已获得道具",
      scoreUnit: "分",
      rank: "排名",
      player: "玩家",
      duration: "耗时",
      empty: "暂无通关记录",
      loading: "正在加载积分板...",
    },
    authGate: {
      loading: "正在载入探险档案...",
    },
    result: {
      title: "寻宝结果",
      headline: "恭喜完成全部关卡！",
      subtitle: "你已成功破解郑芝龙的宝藏之谜",
      score: "总分",
      duration: "耗时",
      items: "道具",
      itemWall: "道具收藏",
      viewLeaderboard: "查看积分板",
      backToMap: "返回地图",
      completedAt: "通关日期",
      screenshotHint: "截图保存这份通关记录",
    },
  },
  en: {
    brand: "Zheng Zhilong's Treasure II",
    gameName: "Zheng Zhilong's Treasure II",
    metaDescription: "A yellowed note, a Ming dynasty poem, and a 400-year treasure hunt across the library.",
    login: {
      headline: "Zheng Zhilong's Treasure II",
      description: "Enter your student ID (2 letters + 6 digits) to start the hunt.\nExample: ab565666",
      adminDescription: "Admins sign in with their account and password.",
      staffDescription: "Enter your staff username to start the hunt.\nUsername length: 5-15 characters.",
      studentIdLabel: "Account / Student ID",
      studentIdPlaceholder: "Enter student ID",
      passwordLabel: "Password",
      passwordPlaceholder: "Enter password",
      submit: "Start Decoding",
      loading: "Waking the decoding system...",
      invalidStudentId: "Student ID must be 2 letters followed by 6 digits (e.g., ab565666).",
      passwordRequired: "Enter your password.",
      adminRole: "Admin",
      roleLabel: "Login as",
      studentRole: "Student",
      staffRole: "Staff",
      staffUsernameLabel: "Username",
      staffUsernamePlaceholder: "Enter username (5-15 characters)",
      invalidStaffUsername: "Username must be 5-15 characters.",
    },
    intro: {
      eyebrow: "Prologue · The Cipher Note",
      headline: "Zheng Zhilong's Treasure II",
      subtitle: "A yellowed note. A 400-year-old poem. Signed N. Iquan, 1634 — who is he, and where is the treasure?",
      start: "Begin the Hunt",
    },
    header: {
      player: "Player",
      score: "Score",
      items: "Items",
      map: "Map",
      leaderboard: "Leaderboard",
      logout: "Sign in again",
      language: "Language",
    },
    footer: {
      producer: "Produced by the University of Macau Library",
      disclaimer: "This story is a work of fiction. Any resemblance to actual persons or events is purely coincidental.",
    },
    map: {
      title: "Treasure Hunt Map",
      subtitle: ({ completed, total, score }) => `Completed ${completed}/${total} · Score ${score}`,
      routeTitle: "Treasure Trail",
      viewLeaderboard: "View Leaderboard",
      completed: "Completed",
      available: "Available",
      locked: "Locked",
      unlockHint: "Finish the previous level first",
      stamped: "Stamped",
    },
    level: {
      title: (level) => `Level ${level}`,
      storyHeading: "Story Clue",
      questionHeading: "Challenge Question",
      multiSelectHint: "Multiple answers allowed",
      submitAnswer: "Submit Answer",
      answerCorrect: (score) => `You earned ${score} points on this question.`,
      submitError: "Submission failed. Please try again.",
      completedHint: "This level is complete. Continue or return to the map.",
      settlementHeading: "Level Summary",
      questionStatus: "Quiz Status",
      qrStatus: "Pass Status",
      completeAndNext: "Complete & Continue",
      backToMap: "Back to Map",
      completionStoryHeading: "Level Story",
      itemUnlockHint: "Unlocks after completion",
      loading: "Loading level clues...",
      loadError: "Failed to load this level. Please try again.",
      selectFirst: "Choose an answer before submitting.",
      runNotReady: "Your run is not ready yet. Return to the map and try again.",
      attemptsLabel: (attempts) => `${attempts} attempt${attempts === 1 ? "" : "s"}`,
    },
    qr: {
      heading: "Go to Location & Enter Pass Code",
      locationLabel: "Location",
      description: "Use the camera to scan, or enter the pass code manually.",
      scanArea: "Scan area",
      startScan: "Start Scan",
      stopScan: "Stop Scan",
      manualPlaceholder: "Enter QR content",
      manualVerify: "Verify",

      emptyValue: "Please enter the pass code before verifying.",
      success: "Verification successful. The treasure mechanism is now unlocked.",
      mismatch: "Pass code does not match. Try again.",
      cameraError: "Camera unavailable. Use manual input instead.",
    },
    items: {
      panelTitle: "Collected Items",
      panelSubtitle: "Your Collection",
      close: "Close",
      locked: "Locked",
    },
    leaderboard: {
      title: "Leaderboard",
      subtitleComplete: "All levels completed",
      subtitleIncomplete: "Complete more levels to unlock the full board",
      finalScore: "Final Score",
      progress: ({ completed, total }) => `Progress: ${completed}/${total}`,
      backToMap: "Back to Map",
      reset: "Reset",
      levelBreakdown: "Level Breakdown",
      notCompleted: "Not completed",
      itemSection: "Collected Items",
      scoreUnit: "points",
      rank: "Rank",
      player: "Player",
      duration: "Time",
      empty: "No completed runs yet",
      loading: "Loading leaderboard...",
    },
    authGate: {
      loading: "Loading exploration data...",
    },
    result: {
      title: "Treasure Hunt Results",
      headline: "Congratulations on Completing All Levels!",
      subtitle: "You have successfully unraveled the mystery of Zheng Zhilong's treasure",
      score: "Score",
      duration: "Duration",
      items: "Items",
      itemWall: "Item Collection",
      viewLeaderboard: "View Leaderboard",
      backToMap: "Back to Map",
      completedAt: "Completed",
      screenshotHint: "Screenshot to save this record",
    },
  },
};

export const getTranslations = (locale: Locale) =>
  translations[locale] ?? translations[DEFAULT_LOCALE];
