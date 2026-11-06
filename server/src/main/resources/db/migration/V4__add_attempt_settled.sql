-- 通关的两道验证（答题 / 现场扫码）是相互独立的，先做哪个都行。
-- 需要一个显式标记记录「本关已经结算过」，否则最后一个完成的动作
-- 无法区分「第一次结算」和「重复提交」，会重复加分。
ALTER TABLE level_attempt
    ADD COLUMN settled TINYINT NOT NULL DEFAULT 0 COMMENT '1 = 已结算（计分、发道具、推进关卡）'
        AFTER qr_passed;

-- 历史数据：两关都通过的视为已结算
UPDATE level_attempt
SET settled = 1
WHERE question_passed = 1
  AND qr_passed = 1;
