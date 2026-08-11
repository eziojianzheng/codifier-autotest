 WITH zchy AS MATERIALIZED (
                SELECT * FROM "material"."hyb" WHERE zt = 'zhengchang'
            )
            SELECT * FROM zchy