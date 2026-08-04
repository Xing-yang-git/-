-- 08-fix-duplicate-ratings.sql -- 清理重复评分
-- 每个 (borrow_id, from_user_id) 只保留 id 最小的一条
DELETE FROM ratings
WHERE id IN (
  SELECT id FROM (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY borrow_id, from_user_id ORDER BY id) AS rn
    FROM ratings WHERE borrow_id IS NOT NULL
  ) sub WHERE rn > 1
);

-- help_application_id 维度同样的清理
DELETE FROM ratings
WHERE id IN (
  SELECT id FROM (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY help_application_id, from_user_id ORDER BY id) AS rn
    FROM ratings WHERE help_application_id IS NOT NULL
  ) sub WHERE rn > 1
);
