WITH xstj AS MATERIALIZED (
    SELECT 
        s.splx,
        -- 使用COALESCE处理NULL，0作为默认值
        SUM(COALESCE(CAST(d.sl AS DECIMAL), 0)) as zsl,
        SUM(COALESCE(CAST(d.ze AS DECIMAL), 0)) as zje
    FROM "material"."ddb" d
    JOIN "material"."spb" s ON d.spid = s.spid
    GROUP BY s.splx
)
SELECT * FROM xstj
WHERE zje > 100