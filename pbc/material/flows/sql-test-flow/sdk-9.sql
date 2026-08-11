WITH hydd AS MATERIALIZED (
                SELECT 
                    h.hyid,
                    h.hyxm,
                    d.ddid,
                    CAST(d.ze AS DECIMAL) as ze_num
                FROM  "material"."hyb" h
                JOIN  "material"."ddb" d ON h.hyid = d.hyid
            )
            SELECT 
                hyxm,
                SUM(ze_num) as zj
            FROM hydd
            GROUP BY hyxm