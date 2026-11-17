-- Hibernate (ddl-auto) generated a CHECK constraint per @Enumerated(STRING) column. They are never altered when an enum
-- gains a value, which already broke WAITLISTED and ESSAY once. Schema changes are explicit migrations from now on,
-- so drop every enum CHECK; the application layer (Java enums + Bean Validation) remains the source of truth.
DO $$
DECLARE r record;
BEGIN
  FOR r IN
    SELECT conrelid::regclass AS tbl, conname
    FROM pg_constraint
    WHERE contype = 'c'
      AND connamespace = 'public'::regnamespace
      AND pg_get_constraintdef(oid) LIKE '%ANY (ARRAY[%'
  LOOP
    EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', r.tbl, r.conname);
  END LOOP;
END $$;
