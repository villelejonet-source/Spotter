// The owner's usage overview (/admin). The numbers come from the admin_stats() database
// function (supabase/migrations/20261029000000_admin_tag.sql), which answers only for accounts
// tagged admin ("admin": true in their auth app_metadata): there is no allow-list or secret
// key on the website. Dates and counts only, never workout contents.

export interface AdminTotals {
  accounts: number;
  confirmed: number;
  active7d: number;
  active30d: number;
  signedIn30d: number;
  withBackup: number;
  workouts: number;
  devices: number;
}

export interface AdminWeek {
  /** Monday of the week (UTC), as epoch ms. */
  week: number;
  signups: number;
  workouts: number;
  /** Accounts that finished at least one workout that week. */
  activeUsers: number;
}

export interface AdminVersion {
  versionCode: number;
  devices: number;
  accounts: number;
}

export interface AdminUser {
  email: string | null;
  createdAt: number | null;
  lastSignInAt: number | null;
  lastActiveAt: number | null;
  workouts: number;
  devices: number;
  /** versionCode of the account's most recently synced install; null before versionCode 7. */
  versionCode: number | null;
}

export interface AdminStats {
  generatedAt: number;
  totals: AdminTotals;
  byWeek: AdminWeek[];
  versions: AdminVersion[];
  /** Accounts active in the last 30 days that never checked in: versionCode 6 or older, or web only. */
  activeWithoutCheckIn: number;
  users: AdminUser[];
}

const obj = (v: unknown): Record<string, unknown> => (v && typeof v === "object" && !Array.isArray(v) ? (v as Record<string, unknown>) : {});
const arr = (v: unknown): unknown[] => (Array.isArray(v) ? v : []);
const num = (v: unknown): number => (typeof v === "number" && Number.isFinite(v) ? v : 0);
const numOrNull = (v: unknown): number | null => (typeof v === "number" && Number.isFinite(v) ? v : null);
const time = (v: unknown): number | null => {
  if (typeof v !== "string") return null;
  const ms = Date.parse(v);
  return Number.isNaN(ms) ? null : ms;
};

/** admin_stats()'s JSON as typed values; anything missing or malformed reads as 0 or null. */
export function parseAdminStats(raw: unknown): AdminStats {
  const r = obj(raw);
  const t = obj(r.totals);
  return {
    generatedAt: time(r.generated_at) ?? 0,
    totals: {
      accounts: num(t.accounts),
      confirmed: num(t.confirmed),
      active7d: num(t.active_7d),
      active30d: num(t.active_30d),
      signedIn30d: num(t.signed_in_30d),
      withBackup: num(t.with_backup),
      workouts: num(t.workouts),
      devices: num(t.devices),
    },
    byWeek: arr(r.by_week).map((w) => {
      const o = obj(w);
      return {
        week: typeof o.week === "string" ? Date.parse(`${o.week}T00:00:00Z`) : 0,
        signups: num(o.signups),
        workouts: num(o.workouts),
        activeUsers: num(o.active_users),
      };
    }),
    versions: arr(r.versions).map((v) => {
      const o = obj(v);
      return { versionCode: num(o.version_code), devices: num(o.devices), accounts: num(o.accounts) };
    }),
    activeWithoutCheckIn: num(r.active_30d_without_check_in),
    users: arr(r.users).map((u) => {
      const o = obj(u);
      return {
        email: typeof o.email === "string" ? o.email : null,
        createdAt: time(o.created_at),
        lastSignInAt: time(o.last_sign_in_at),
        lastActiveAt: time(o.last_active_at),
        workouts: num(o.workouts),
        devices: num(o.devices),
        versionCode: numOrNull(o.version_code),
      };
    }),
  };
}

/**
 * Whether the session token's claims carry the admin tag: app_metadata.admin true (or "true").
 * Only shows the Admin menu; admin_stats checks the tag in the database again. app_metadata,
 * unlike user_metadata, can't be changed by the user.
 */
export function isAdminClaims(claims: unknown): boolean {
  const admin = obj(obj(claims).app_metadata).admin;
  return admin === true || admin === "true";
}

/** Postgres "insufficient privilege": admin_stats says no to anyone not tagged admin. */
export const NOT_ADMIN = "42501";
