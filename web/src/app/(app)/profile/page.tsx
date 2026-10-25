import { ConfirmButton } from "@/components/confirm-button";
import { DeleteAccount } from "@/components/delete-account";
import { buttonClass, Card, CardTitle, PageHeader } from "@/components/ui";
import { getBodyWeight, getProfile } from "@/lib/data";
import { APP_BACKUP_PATH } from "@/lib/labels";
import { requireUser } from "@/lib/supabase/server";
import { profileDraftFrom } from "@/lib/sync-writer";
import { signOut } from "@/app/actions/account";
import { ChangePassword } from "./change-password";
import { ProfileForm } from "./profile-form";
import { WeightLog } from "./weight-log";

export const metadata = { title: "Profile" };

export default async function ProfilePage() {
  // The email comes from the session's claims (checked locally), not a round trip to Supabase.
  const [{ user }, profile, bodyWeight] = await Promise.all([requireUser(), getProfile(), getBodyWeight()]);
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
            <div className="space-y-3 text-sm">
              <p className="text-muted">
                No profile synced yet. The app&apos;s two-minute questionnaire sets it up and builds your first plan: install Spotter, sign in under {APP_BACKUP_PATH} with
                this email, and it appears here after the first sync.
              </p>
              <details className="rounded-lg border border-outline p-3">
                <summary className="cursor-pointer font-medium">Don&apos;t have the app yet? Set up your profile here</summary>
                <div className="mt-3">
                  <ProfileForm create />
                </div>
              </details>
            </div>
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
              <a href="/export?format=json" className={buttonClass()}>
                Everything (JSON)
              </a>
              <a href="/export?format=csv" className={buttonClass()}>
                Sets (CSV)
              </a>
            </div>
          </Card>
          <Card>
            <CardTitle>Account</CardTitle>
            <dl className="text-sm">
              <div className="flex justify-between gap-4 py-2.5">
                <dt className="text-muted">Email</dt>
                <dd className="truncate text-right">{user.email}</dd>
              </div>
            </dl>
            <ConfirmButton
              action={signOut}
              title="Sign out?"
              body="You can sign in again any time. Your backup stays in the cloud and keeps syncing from your phone."
              confirmLabel="Sign out"
              className="mt-1"
            >
              Sign out
            </ConfirmButton>
            <div className="mt-2 border-t border-outline pt-4">
              <ChangePassword />
            </div>
            <div className="mt-4 border-t border-outline pt-4">
              <DeleteAccount email={user.email} />
            </div>
          </Card>
        </div>
      </div>
    </>
  );
}
