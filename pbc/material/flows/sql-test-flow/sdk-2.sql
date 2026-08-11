WITH 
            suoyouhy AS (
                SELECT * FROM "material"."hyb"
            ),
            gsp AS MATERIALIZED (
                SELECT * FROM "material"."spb" WHERE spjg > 1000
            )
            SELECT h.hyxm, s.spmc
            FROM suoyouhy h
            CROSS JOIN gsp s
            WHERE h.zt = 'zhengchang'