-- The schema-wide access rules, checked after every migration in supabase/migrations/ has
-- been applied. A new table, view or function that breaks one fails here, in CI, before it
-- can reach production. Read-only: nothing is created or changed.
--
--   docker exec -i supabase_db_spotter psql -U postgres -d postgres -v ON_ERROR_STOP=1 -q -t < supabase/tests/rls.sql
--
-- Rules, for everything in the public schema (the part the Data API exposes):
-- 1. Every table has row-level security on. With RLS on and no policy, nobody can read or
--    write it through the API, which is the safe default for a new table.
-- 2. Every view runs with the caller's rights (security_invoker), so the RLS of the tables
--    it reads still applies. A plain view runs as its owner and would bypass RLS.
-- 3. Every SECURITY DEFINER function pins search_path, so it can't be tricked into calling
--    an object someone else created.
-- 4. The anon role (no signed-in user) has no privileges on any table or view.

do $$
declare
    bad text;
begin
    select string_agg(c.relname, ', ' order by c.relname) into bad
    from pg_class c join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public' and c.relkind in ('r', 'p') and not c.relrowsecurity;
    if bad is not null then
        raise exception 'rls: row-level security is off on: %', bad;
    end if;

    select string_agg(c.relname, ', ' order by c.relname) into bad
    from pg_class c join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public' and c.relkind in ('v', 'm')
      and not coalesce('security_invoker=true' = any(c.reloptions), false);
    if bad is not null then
        raise exception 'rls: views without security_invoker = true (they bypass RLS): %', bad;
    end if;

    select string_agg(p.proname, ', ' order by p.proname) into bad
    from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.prosecdef
      and not exists (select 1 from unnest(coalesce(p.proconfig, '{}')) s where s like 'search_path=%');
    if bad is not null then
        raise exception 'rls: SECURITY DEFINER functions without a fixed search_path: %', bad;
    end if;

    select string_agg(distinct table_name || ' (' || privilege_type || ')', ', ') into bad
    from information_schema.role_table_grants
    where table_schema = 'public' and grantee = 'anon';
    if bad is not null then
        raise exception 'rls: the anon role has privileges on: %', bad;
    end if;

    raise notice 'rls: all checks passed';
end;
$$;
