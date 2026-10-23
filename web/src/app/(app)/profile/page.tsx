import { DeleteAccount } from "@/components/delete-account";
import { Badge, Card, CardTitle, PageHeader } from "@/components/ui";
import { getProfile } from "@/lib/data";
import { requireUser } from "@/lib/supabase/server";
import { enumLabel, formatWeight, GOAL_LABELS, splitEnumList } from "@/lib/model";

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex justify-between gap-4 py-2.5 text-sm">
      <dt className="text-muted">{label}</dt>
      <dd className="text-right">{children}</dd>
    </div>
  );
}

function Chips({ value }: { value: string }) {
  const items = splitEnumList(value);
  if (!items.length) return <span className="text-muted">None</span>;
  return (
    <span className="flex flex-wrap justify-end gap-1">
      {items.map((i) => (
        <Badge key={i}>{enumLabel(i)}</Badge>
      ))}
    </span>
  );
}

export default async function ProfilePage() {
  const [{ user }, profile] = await Promise.all([requireUser(), getProfile()]);

  return (
    <>
      <PageHeader title="Profile" subtitle="Edit your training profile in the app; it syncs here." />
      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardTitle>Training profile</CardTitle>
          {profile ? (
            <dl className="divide-y divide-outline">
              <Row label="Goal">{GOAL_LABELS[profile.goal] ?? enumLabel(profile.goal)}</Row>
              <Row label="Experience">{enumLabel(profile.experience)}</Row>
              <Row label="Days per week">{profile.days_per_week}</Row>
              <Row label="Session length">{profile.session_length_minutes} min</Row>
              <Row label="Body weight">{formatWeight(profile.body_weight_kg, profile.units)}</Row>
              <Row label="Height">{Math.round(profile.height_cm)} cm</Row>
              <Row label="Units">{profile.units.toLowerCase()}</Row>
              <Row label="Focus areas"><Chips value={profile.focus_areas} /></Row>
              <Row label="Limitations"><Chips value={profile.limitations} /></Row>
              <Row label="Equipment"><Chips value={profile.equipment} /></Row>
            </dl>
          ) : (
            <p className="text-sm text-muted">No profile synced yet. Finish onboarding in the app with cloud backup on.</p>
          )}
        </Card>

        <div className="space-y-4">
          <Card>
            <CardTitle>Account</CardTitle>
            <dl className="divide-y divide-outline">
              <Row label="Email">{user.email}</Row>
              <Row label="Member since">{new Date(user.created_at).toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric" })}</Row>
            </dl>
          </Card>
          <Card>
            <CardTitle>Delete account</CardTitle>
            <DeleteAccount />
          </Card>
        </div>
      </div>
    </>
  );
}
