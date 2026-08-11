 WITH waiceng AS MATERIALIZED (
                WITH neiceng AS MATERIALIZED (
                    SELECT * FROM "material"."ddb" WHERE CAST(sl as DECIMAL) > 1
                )
                SELECT * FROM neiceng
            )
            SELECT COUNT(*) as js FROM waiceng