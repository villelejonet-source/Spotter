import { notFound } from "next/navigation";
import { BarChartView } from "@/components/charts";
import { Card, CardTitle, EmptyState, PageHeader, Stat } from "@/components/ui";
import { NOT_ADMIN, parseAdminStats } from "@/lib/admin";
import { formatDateValue, formatFullDate, formatRelative } from "@/lib/dates";
import { requireUser } from "@/lib/supabase/server";

// No title: a refused visitor's tab shouldn't name the page.
export const metadata = { robots: { index: false, follow: false } };

const WEEKS = 12;

/**
 * The owner's usage overview. In the navigation only for accounts tagged admin. admin_stats()
 * checks the tag in the database, so the page follows it at once (the menu follows when the
 * session token refreshes), and everyone else gets the ordinary "not found" page.
 */
export default async function AdminPage() {
  const { supabase } = await requireUser();
  const { data, error } = await supabase.rpc("admin_stats", { weeks: WEEKS });
  if (error?.code === NOT_ADMIN) notFound();
  if (error) throw new Error(`admin_stats failed (${error.code ?? "unknown"})`);
  const stats = parseAdminStats(data);
  const { totals } = stats;
  const week = (p: { week: number }) => formatDateValue(p.week);

  return (
    <>
      <PageHeader title="Usage" subtitle={`Dates and counts only. Updated ${formatFullDate(stats.generatedAt)}.`} />
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Stat label="Accounts" value={totals.accounts} hint={`${totals.confirmed} confirmed · ${totals.withBackup} with a backup`} />
        <Stat label="Active, 7 days" value={totals.active7d} hint="synced from app or website" />
        <Stat label="Active, 30 days" value={totals.active30d} hint={`${totals.signedIn30d} signed in again`} />
        <Stat label="Workouts" value={totals.workouts} hint={`finished, all accounts · ${totals.devices} ${totals.devices === 1 ? "install" : "installs"}`} />
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-3">
        <Card>
          <CardTitle>Sign-ups per week</CardTitle>
          <BarChartView data={stats.byWeek.map((w) => ({ label: week(w), value: w.signups }))} height={200} />
        </Card>
        <Card>
          <CardTitle>Workouts per week</CardTitle>
          <BarChartView data={stats.byWeek.map((w) => ({ label: week(w), value: w.workouts }))} height={200} />
        </Card>
        <Card>
          <CardTitle>Weekly active users</CardTitle>
          <BarChartView data={stats.byWeek.map((w) => ({ label: week(w), value: w.activeUsers }))} height={200} />
          <p className="mt-2 text-xs text-muted">Accounts that finished a workout that week.</p>
        </Card>
      </div>

      <Card className="mt-4">
        <CardTitle>App versions, last 30 days</CardTitle>
        {stats.versions.length === 0 ? (
          <p className="text-sm text-muted">No install has checked in yet (from versionCode 7).</p>
        ) : (
          <table className="w-full text-sm">
            <thead className="text-left text-muted">
              <tr>
                <th className="py-1 font-medium">versionCode</th>
                <th className="py-1 text-right font-medium">Installs</th>
                <th className="py-1 text-right font-medium">Accounts</th>
              </tr>
            </thead>
            <tbody>
              {stats.versions.map((v) => (
                <tr key={v.versionCode} className="border-t border-outline">
                  <td className="py-1.5">{v.versionCode}</td>
                  <td className="py-1.5 text-right tabular-nums">{v.devices}</td>
                  <td className="py-1.5 text-right tabular-nums">{v.accounts}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <p className="mt-3 text-xs text-muted">
          {stats.activeWithoutCheckIn} active {stats.activeWithoutCheckIn === 1 ? "account has" : "accounts have"} never checked in: an app on
          versionCode 6 or older, or the website only. Trainers wait until no trainer runs one of those.
        </p>
      </Card>

      <Card className="mt-4">
        <CardTitle>Accounts</CardTitle>
        {stats.users.length === 0 ? (
          <EmptyState title="No accounts yet" />
        ) : (
          <div className="-mx-2 overflow-x-auto">
            <table className="w-full min-w-[640px] text-sm">
              <thead className="text-left text-muted">
                <tr>
                  <th className="px-2 py-1 font-medium">Email</th>
                  <th className="px-2 py-1 font-medium">Signed up</th>
                  <th className="px-2 py-1 font-medium">Last sign-in</th>
                  <th className="px-2 py-1 font-medium">Last active</th>
                  <th className="px-2 py-1 text-right font-medium">Workouts</th>
                  <th className="px-2 py-1 text-right font-medium">App</th>
                </tr>
              </thead>
              <tbody>
                {stats.users.map((u, i) => (
                  <tr key={u.email ?? i} className="border-t border-outline">
                    <td className="max-w-[16rem] truncate px-2 py-1.5" title={u.email ?? undefined}>{u.email ?? "–"}</td>
                    <td className="px-2 py-1.5 whitespace-nowrap">{u.createdAt ? formatDateValue(u.createdAt) : "–"}</td>
                    <td className="px-2 py-1.5 whitespace-nowrap">{u.lastSignInAt ? formatRelative(u.lastSignInAt) : "–"}</td>
                    <td className="px-2 py-1.5 whitespace-nowrap">{u.lastActiveAt ? formatRelative(u.lastActiveAt) : "never synced"}</td>
                    <td className="px-2 py-1.5 text-right tabular-nums">{u.workouts}</td>
                    <td className="px-2 py-1.5 text-right tabular-nums">{u.versionCode ?? (u.lastActiveAt ? "≤ 6" : "–")}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <p className="mt-3 text-xs text-muted">
          Last active is the newest of the account&apos;s synced rows and app check-ins; the app stays signed in for months, so last sign-in
          says little.
        </p>
      </Card>
    </>
  );
}
