WITH xstj AS MATERIALIZED (
                SELECT 
                    s.splx,
                    SUM(d.sl) as zsl,
                    SUM(d.ze) as zje
                FROM "material"."ddb" d
                JOIN "material"."spb" s ON d.spid = s.spid
                GROUP BY s.splx
            )
            SELECT * FROM xstj
            WHERE zje > 100