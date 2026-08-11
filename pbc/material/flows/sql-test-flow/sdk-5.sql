 WITH waiceng AS MATERIALIZED (
                WITH neiceng AS MATERIALIZED (
                    SELECT * FROM "material"."ddb" WHERE sl > 1
                )
                SELECT * FROM neiceng
            )
            SELECT COUNT(*) as js FROM waiceng