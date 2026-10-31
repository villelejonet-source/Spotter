import Link from "next/link";
import { PrivacyPolicy } from "@/components/privacy-policy";
import { Card } from "@/components/ui";

export const metadata = { title: "Privacy policy" };

/**
 * Public, signed in or out (the middleware lets it through): the Play listing links here.
 * The text is the one in docs/play-store/privacy-policy.md; edit it there.
 */
export default function PrivacyPage() {
  return (
    <main className="mx-auto max-w-2xl px-4 py-10">
      <Link href="/" className="tap rounded-md text-sm font-semibold text-primary">
        Spotter
      </Link>
      <Card className="mt-4 p-6 sm:p-8">
        <PrivacyPolicy />
      </Card>
    </main>
  );
}
