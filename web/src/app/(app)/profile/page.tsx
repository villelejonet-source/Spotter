import { DeleteAccount } from "@/components/delete-account";
import { buttonClass, Card, CardTitle, PageHeader } from "@/components/ui";
import { getBodyWeight, getProfile } from "@/lib/data";
import { requireUser } from "@/lib/supabase/server";
import { profileDraftFrom } from "@/lib/sync-writer";
import { ProfileForm } from "./profile-form";
import { WeightLog } from "./weight-log";

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex justify-between gap-4 py-2.5 text-sm">
      <dt className="text-muted">{label}</dt>
      <dd className="text-right">{children}</dd>
    </div>
  );
}

export default async function ProfilePage() {
  const { supabase } = await requireUser();
  const [{ data: { user } }, profile, bodyWeight] = await Promise.all([supabase.auth.getUser(), getProfile(), getBodyWeight()]);
  if (!user) return null;
  const unit = profile?.units ?? "KG";

  return (
    <>
      <PageHeader title="Profile" subtitle="Changes here sync to the app the next time it opens." />
      <div className="grid gap-4 lg:grid-cols-[3fr_2fr]">
        <Card>
          <CardTitle>Training profile</CardTitle>
          {profile ? (
            <ProfileForm initial={profileDraftFrom(profile)} version={profile.updated_at} />
          ) : (
            <p className="text-sm text-muted">No profile synced yet. Finish onboarding in the app with cloud backup on.</p>
          )}
        </Card>

        <div className="space-y-4">
          <Card>
            <CardTitle>Body weight</CardTitle>
            <WeightLog entries={bodyWeight} unit={unit} />
          </Card>
          <Card>
            <CardTitle>Your data</CardTitle>
            <p className="mb-3 text-sm text-muted">Download everything Spotter has backed up for you.</p>
            <div className="flex flex-wrap gap-2">
              <a href="/export?format=json" className={buttonClass()}>Everything (JSON)</a>
              <a href="/export?format=csv" className={buttonClass()}>Sets (CSV)</a>
            </div>
          </Card>
          <Card>
            <CardTitle>Account</CardTitle>
            <dl className="divide-y divide-outline">
              <Row label="Email">{user.email}</Row>
              <Row label="Member since">{new Date(user.created_at).toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric" })}</Row>
            </dl>
            <div className="mt-4">
              <DeleteAccount />
            </div>
          </Card>
        </div>
      </div>
    </>
  );
}
