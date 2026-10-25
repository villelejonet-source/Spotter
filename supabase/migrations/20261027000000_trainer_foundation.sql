-- Trainer foundation (2026-10). Safe to re-run.
--
-- The groundwork for personal trainers (PLAN.md "Platform roadmap", step 3), with trainers
-- still switched off: nobody can read anyone else's rows after this migration.
--
-- 1. profiles: a display name per account. Not the synced user_profile rows (v_profile),
--    which hold the app's settings; any account can be a trainer, a client or both.
-- 2. trainer_links: trainer, client, status (pending | active | ended), an unguessable
--    invite code and an optional label the trainer sees until the invite is accepted. A client has at most one trainer at a time; a trainer, any number of clients. Each side reads its own links; writes go only through
--    create_trainer_invite, accept_trainer_invite and end_trainer_link.
-- 3. can_read_user(owner_id): the one access check for reading synced rows. Today it is
--    owner-only; letting an active trainer read is a change to this function alone (see
--    docs/platform/infrastructure.md, "Trainer foundation", for the steps before that).
-- 4. delete_my_account also removes the account's profile and trainer links.
--
-- This is now the last migration in order, so it owns the final definitions of
-- "sync_rows: read own" and delete_my_account (20261026000000_audit_hardening.sql had them
-- before; its other definitions still stand).
--
-- Kept on purpose (noted for the advisors):
-- - can_read_user has no SET search_path and is SECURITY INVOKER: either would stop Postgres
--   inlining it into the sync_rows policy, which would then call a function per row instead
--   of comparing user_id with an InitPlan. Every name in it is schema-qualified, so the
--   "function search_path mutable" WARN for it is expected.
-- - create_trainer_invite, accept_trainer_invite, end_trainer_link and delete_my_account are
--   SECURITY DEFINER with search_path = '': trainer_links is closed to direct writes, and each
--   function only ever acts for auth.uid().

-- ---- 1. Profiles ----

create table if not exists public.profiles (
    user_id      uuid        primary key default auth.uid() references auth.users (id) on delete cascade,
    display_name text        check (char_length(display_name) between 1 and 80),
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now()
);

create or replace function public.profiles_touch_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    new.updated_at := now();
    return new;
end;
$$;

revoke all on function public.profiles_touch_updated_at() from public, anon, authenticated;

drop trigger if exists profiles_touch_updated_at on public.profiles;
create trigger profiles_touch_updated_at
    before update on public.profiles
    for each row execute function public.profiles_touch_updated_at();

alter table public.profiles enable row level security;

drop policy if exists "profiles: read own" on public.profiles;
create policy "profiles: read own" on public.profiles
    for select to authenticated using ((select auth.uid()) = user_id);

drop policy if exists "profiles: insert own" on public.profiles;
create policy "profiles: insert own" on public.profiles
    for insert to authenticated with check ((select auth.uid()) = user_id);

drop policy if exists "profiles: update own" on public.profiles;
create policy "profiles: update own" on public.profiles
    for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

-- A user creates their row and changes only the name; delete_my_account removes it.
revoke all on table public.profiles from public, anon, authenticated;
grant select, insert (user_id, display_name), update (display_name) on table public.profiles to authenticated;

-- ---- 2. Trainer links ----

-- A trainer creates a pending link with an invite code and shares the code; whoever accepts
-- it becomes the client. client_id stays null until then.
create table if not exists public.trainer_links (
    id          uuid        primary key default gen_random_uuid(),
    trainer_id  uuid        not null references auth.users (id) on delete cascade,
    client_id   uuid        references auth.users (id) on delete cascade,
    status      text        not null default 'pending',
    -- 32 hex characters from a random UUID: about 122 random bits.
    invite_code text        not null default replace(gen_random_uuid()::text, '-', ''),
    -- The trainer's own note on a pending invite ("Anna", "Tuesday group"), so several open
    -- invites can be told apart. Cleared when the invite is accepted.
    label       text        check (char_length(label) between 1 and 60),
    created_at  timestamptz not null default now(),
    accepted_at timestamptz,
    ended_at    timestamptz,
    constraint trainer_links_status check (status in ('pending', 'active', 'ended')),
    constraint trainer_links_not_self check (trainer_id <> client_id),
    constraint trainer_links_state check (
        (status = 'pending' and client_id is null and accepted_at is null and ended_at is null)
        or (status = 'active' and client_id is not null and accepted_at is not null and ended_at is null)
        or (status = 'ended' and ended_at is not null)
    ),
    constraint trainer_links_invite_code_key unique (invite_code)
);

-- A client has at most one trainer at a time (a trainer can have any number of clients).
-- After a link ends, the client can link again, with the same trainer or another.
create unique index if not exists trainer_links_one_trainer_key
    on public.trainer_links (client_id)
    where status = 'active';

create index if not exists trainer_links_trainer_idx on public.trainer_links (trainer_id);
create index if not exists trainer_links_client_idx on public.trainer_links (client_id);

alter table public.trainer_links enable row level security;

drop policy if exists "trainer_links: read own" on public.trainer_links;
create policy "trainer_links: read own" on public.trainer_links
    for select to authenticated
    using ((select auth.uid()) = trainer_id or (select auth.uid()) = client_id);

revoke all on table public.trainer_links from public, anon, authenticated;
grant select on table public.trainer_links to authenticated;

-- A new pending link with the caller as trainer and an optional [label] (1-60 characters).
-- Errors: 42501 not signed in, 54000 too many open invites, 23514 label too long or empty.
create or replace function public.create_trainer_invite(label text default null)
returns public.trainer_links
language plpgsql
security definer
set search_path = ''
as $$
declare
    uid uuid := auth.uid();
    open_cap constant integer := 20;
    link public.trainer_links;
begin
    if uid is null then
        raise exception 'not signed in' using errcode = '42501';
    end if;
    if (select count(*) from public.trainer_links t
        where t.trainer_id = uid and t.status = 'pending' and t.created_at > now() - interval '14 days') >= open_cap then
        raise exception 'create_trainer_invite: at most % open invites', open_cap using errcode = '54000';
    end if;
    insert into public.trainer_links (trainer_id, label) values (uid, create_trainer_invite.label) returning * into link;
    return link;
end;
$$;

-- Makes the caller the client of a pending invite less than 14 days old. A caller can only
-- ever make themselves the client, never activate a link for someone else. Errors: 42501 not
-- signed in; P0002 for an unknown, expired, used or own code (one message, so a code can't be
-- probed); 23505 when the caller already has a trainer (they end that link first).
create or replace function public.accept_trainer_invite(code text)
returns public.trainer_links
language plpgsql
security definer
set search_path = ''
as $$
declare
    uid uuid := auth.uid();
    link public.trainer_links;
begin
    if uid is null then
        raise exception 'not signed in' using errcode = '42501';
    end if;
    select * into link from public.trainer_links t
    where t.invite_code = code
      and t.status = 'pending'
      and t.created_at > now() - interval '14 days'
      and t.trainer_id <> uid
    for update;
    if not found then
        raise exception 'accept_trainer_invite: this invite code is not valid' using errcode = 'P0002';
    end if;
    if exists (select 1 from public.trainer_links t where t.client_id = uid and t.status = 'active') then
        raise exception 'accept_trainer_invite: you already have a trainer' using errcode = '23505';
    end if;
    update public.trainer_links t
    set client_id = uid, status = 'active', accepted_at = now(), label = null
    where t.id = link.id
    returning * into link;
    return link;
end;
$$;

-- Ends a pending or active link the caller is either side of: a client can leave, and a
-- trainer can withdraw an invite or stop coaching, at any time. Ending an ended link does
-- nothing. Errors: 42501 not signed in, P0002 not the caller's link.
create or replace function public.end_trainer_link(link_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    uid uuid := auth.uid();
begin
    if uid is null then
        raise exception 'not signed in' using errcode = '42501';
    end if;
    if not exists (
        select 1 from public.trainer_links t
        where t.id = link_id and (t.trainer_id = uid or t.client_id = uid)
    ) then
        raise exception 'end_trainer_link: no such link' using errcode = 'P0002';
    end if;
    update public.trainer_links t
    set status = 'ended', ended_at = now()
    where t.id = link_id and t.status <> 'ended';
end;
$$;

revoke all on function public.create_trainer_invite(text) from public, anon;
revoke all on function public.accept_trainer_invite(text) from public, anon;
revoke all on function public.end_trainer_link(uuid) from public, anon;
grant execute on function public.create_trainer_invite(text) to authenticated;
grant execute on function public.accept_trainer_invite(text) to authenticated;
grant execute on function public.end_trainer_link(uuid) to authenticated;

-- ---- 3. One access check for synced rows ----

-- Whether the signed-in user may read [owner_id]'s synced rows: today only their own. Kept
-- inlinable (plain SQL, invoker, no SET clause), so the policy below plans as
-- user_id = (select auth.uid()). The trainer version adds
--   or exists (select 1 from public.trainer_links t where t.trainer_id = (select auth.uid())
--              and t.client_id = owner_id and t.status = 'active')
-- which stays inlinable: a trainer can already read their own links.
create or replace function public.can_read_user(owner_id uuid)
returns boolean
language sql
stable
security invoker
as $$
    select coalesce(owner_id = (select auth.uid()), false)
$$;

revoke all on function public.can_read_user(uuid) from public, anon;
grant execute on function public.can_read_user(uuid) to authenticated;

drop policy if exists "sync_rows: read own" on public.sync_rows;
create policy "sync_rows: read own" on public.sync_rows
    for select to authenticated using (public.can_read_user(user_id));

-- ---- 4. Account deletion ----

-- As in 20261026000000, plus the profile and every link the account is either side of.
create or replace function public.delete_my_account()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    uid uuid := auth.uid();
begin
    if uid is null then
        raise exception 'not signed in' using errcode = '42501';
    end if;
    delete from public.trainer_links where trainer_id = uid or client_id = uid;
    delete from public.profiles where user_id = uid;
    delete from public.sync_rows where user_id = uid;
    delete from auth.users where id = uid;
end;
$$;

revoke all on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;
