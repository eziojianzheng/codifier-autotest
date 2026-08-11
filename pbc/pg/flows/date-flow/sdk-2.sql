SELECT T.id,
    CAST(T."input" AS DATE)        AS inputD,
    T."input",
    T."datePicker",
    CAST(T."datePicker" AS DATE)  AS datePickerD
FROM "pg"."date-form" AS T where T.deleted=0
