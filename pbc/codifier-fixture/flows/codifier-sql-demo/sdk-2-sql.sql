-- Codifier 拆分测试 SQL — 应被提取为独立 .sql 文件
-- 查询产品数量（示意，含注释与 JOIN 结构）
SELECT COUNT(*) AS total
FROM form_entity_data d
WHERE d.deleted = 0
