WITH hydd AS MATERIALIZED (
                SELECT 
                    h.hyid,
                    h.hyxm,
                    d.ddid,
                    d.ze
                FROM  "material"."hyb" h
                JOIN  "material"."ddb" d ON h.hyid = d.hyid
            )
            SELECT 
                hyxm,
                SUM(ze) as zj
            FROM hydd
            GROUP BY hyxm