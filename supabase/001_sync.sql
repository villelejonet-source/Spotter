-- Spotter cloud backup + sync (milestone 8).
-- Run once in the Supabase dashboard: SQL Editor → New query → paste → Run.
-- Safe to re-run.

-- One row per synced record (a workout, a set, a plan day, ...). The app owns the
-- schema of `payload`, so local database changes never need a server migration.
create table if not exists public.sync_rows (
    user_id           uuid        not null default auth.uid() references auth.users (id) on delete cascade,
    table_name        text        not null,
    sync_id           text        not null,
    payload           jsonb,
    -- Client clock (ms) of the last change: decides last-write-wins between devices.
    updated_at        bigint      not null,
    -- Set when the record was deleted (soft delete, so other devices learn about it).
    deleted_at        bigint,
    -- Server clock: the cursor devices pull from.
    server_updated_at timestamptz not null default clock_timestamp(),
    primary key (user_id, table_name, sync_id)
);

create index if not exists sync_rows_pull_idx on public.sync_rows (user_id, server_updated_at);

-- Row-level security: every user can only ever see and change their own rows.
alter table public.sync_rows enable row level security;

drop policy if exists "sync_rows: read own" on public.sync_rows;
create policy "sync_rows: read own" on public.sync_rows
    for select to authenticated using (auth.uid() = user_id);

drop policy if exists "sync_rows: insert own" on public.sync_rows;
create policy "sync_rows: insert own" on public.sync_rows
    for insert to authenticated with check (auth.uid() = user_id);

drop policy if exists "sync_rows: update own" on public.sync_rows;
create policy "sync_rows: update own" on public.sync_rows
    for update to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id);

drop policy if exists "sync_rows: delete own" on public.sync_rows;
create policy "sync_rows: delete own" on public.sync_rows
    for delete to authenticated using (auth.uid() = user_id);

-- Upload a batch of changes. A row only overwrites the stored one if it is newer
-- (last-write-wins); re-sending an unchanged row is a no-op, so other devices don't
-- pull it again. Runs with the caller's rights, so RLS still applies.
create or replace function public.push_rows(rows jsonb)
returns integer
language sql
security invoker
set search_path = public
as $$
    with incoming as (
        select *
        from jsonb_to_recordset(rows) as r(table_name text, sync_id text, payload jsonb, updated_at bigint, deleted_at bigint)
    ),
    written as (
        insert into public.sync_rows (user_id, table_name, sync_id, payload, updated_at, deleted_at, server_updated_at)
        select auth.uid(), table_name, sync_id, payload, updated_at, deleted_at, clock_timestamp()
        from incoming
        on conflict (user_id, table_name, sync_id) do update
            set payload = excluded.payload,
                updated_at = excluded.updated_at,
                deleted_at = excluded.deleted_at,
                server_updated_at = clock_timestamp()
            where excluded.updated_at > sync_rows.updated_at
        returning 1
    )
    select count(*)::integer from written;
$$;

revoke all on function public.push_rows(jsonb) from public, anon;
grant execute on function public.push_rows(jsonb) to authenticated;

-- In-app account deletion (required by Google Play): removes the signed-in user, and
-- with it (on delete cascade) every row of their backup.
create or replace function public.delete_my_account()
returns void
language sql
security definer
set search_path = public, auth
as $$
    delete from auth.users where id = auth.uid();
$$;

revoke all on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;
