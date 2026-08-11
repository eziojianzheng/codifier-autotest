
WITH CAT_LATEST AS (
    SELECT *,
        ROW_NUMBER() OVER (PARTITION BY "cat_id" ORDER BY "update_type" DESC) as rn
    FROM "catmanagement"."cats"
    WHERE "status" = 'active'
      AND "intake_date" = '2024-01-01'
)


, HEALTH_LATEST AS (
    SELECT *,
        ROW_NUMBER() OVER (PARTITION BY "health_id" ORDER BY "update_type" DESC) as rn
    FROM "catmanagement"."health-records" 
    WHERE "status" = 'active'
)


, ADOPTION_LATEST AS (
    SELECT *,
        ROW_NUMBER() OVER (PARTITION BY "application_id" ORDER BY "update_type" DESC) as rn
    FROM "catmanagement"."adoption-applications"
    WHERE "status" = 'active'
)


, CAT_STATUS AS (
    SELECT 
        "cat_id",
        MAX(CASE WHEN "update_type" = 'auto' THEN last_checkup END) as auto_checkup,
        MAX(CASE WHEN "update_type" = 'manual' THEN last_checkup END) as manual_checkup
    FROM CAT_LATEST
    GROUP BY "cat_id"
)

-- 5. 健康评分汇总
, HEALTH_SCORE AS (
    SELECT
        "cat_id",
        AVG(CAST("health_score" AS numeric)) as avg_health_score,
        COUNT(*) as checkup_count
    FROM HEALTH_LATEST
    WHERE rn = 1
    GROUP BY "cat_id"
)

-- 6. 特殊需求猫咪识别
, SPECIAL_NEEDS_CATS AS (
    SELECT
        H."cat_id",
        COUNT(A."special_flag") as special_needs_count
    FROM HEALTH_LATEST H
    LEFT JOIN ADOPTION_LATEST A
      ON A."cat_id" = H."cat_id"
     AND A."care_level" = H."care_level"
    WHERE H.rn = 1
      AND A.rn = 1
      AND (H."medical_notes" IS NULL OR TRIM(H."medical_notes") = '')
      AND A."special_flag" = '1'
    GROUP BY H."cat_id"
)

-- 主查询：查找可收养的猫咪
SELECT
    C."cat_id",
    C."cat_name",
    C."breed",
    C."color",
    H."health_status",
    H."vaccination_status",
    CASE
      WHEN C."microchip_id" IS NOT NULL AND TRIM(C."microchip_id") <> '' THEN '已植入芯片'
      ELSE '未植入芯片'
    END as "microchip_status",
    CS."auto_checkup",
    CS."manual_checkup",
    COALESCE(HS.avg_health_score, 0) as health_score,
    CASE
      WHEN COALESCE(SN.special_needs_count, 0) = 0 THEN '普通猫咪'
      WHEN COALESCE(SN.special_needs_count, 0) >= 1 AND (C."special_care_apply" IS NULL OR TRIM(C."special_care_apply") = '') THEN '特殊需求(待申请)'
      WHEN COALESCE(SN.special_needs_count, 0) >= 1 AND (C."special_care_apply" IS NOT NULL AND TRIM(C."special_care_apply") <> '') THEN '特殊需求(已申请)'
      ELSE '普通猫咪'
    END as special_needs_status
FROM CAT_LATEST C
LEFT JOIN CAT_STATUS CS ON C."cat_id" = CS."cat_id"
LEFT JOIN HEALTH_LATEST H ON C."cat_id" = H."cat_id" AND H.rn = 1
LEFT JOIN HEALTH_SCORE HS ON C."cat_id" = HS."cat_id"
LEFT JOIN SPECIAL_NEEDS_CATS SN ON C."cat_id" = SN."cat_id"
WHERE C.rn = 1

-- 猫咪名字搜索
AND (
  '' = ''
  OR UPPER(C."cat_name") LIKE UPPER('') || '%'
)

-- 品种筛选
AND (
  '' = ''
  OR EXISTS (
      SELECT 1
      FROM HEALTH_LATEST HL
      WHERE HL.rn = 1
        AND HL."cat_id" = C."cat_id"
        AND HL."breed_type" = ''
  )
)

-- 健康状态筛选
AND (
  '' = ''
  OR H."health_status" = ''
  OR H."vaccination_status" = ''
)

-- 特殊需求筛选
AND (
  '' = ''
  OR ('' = 'needs_care' AND C."special_care_apply" IS NOT NULL AND TRIM(C."special_care_apply") <> '')
  OR ('' = 'no_care' AND (C."special_care_apply" IS NULL OR TRIM(C."special_care_apply") = ''))
)

GROUP BY
    C."cat_id", C."cat_name", C."breed", C."color", H."health_status", H."vaccination_status",
    C."microchip_id", CS.auto_checkup, CS.manual_checkup, C."special_care_apply",
    SN.special_needs_count, HS.avg_health_score