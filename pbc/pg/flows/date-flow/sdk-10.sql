SELECT T.id,

    T."input",
    T."datePicker"
FROM "pg"."date-form" AS T where T.deleted=0
