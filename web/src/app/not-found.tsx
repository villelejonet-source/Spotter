import { AuthCard } from "@/components/auth-ui";
import { LinkButton } from "@/components/ui";

/** An address that doesn't exist, outside the signed-in pages (those have their own). */
export default function NotFound() {
  return (
    <AuthCard title="Page not found" subtitle="That address doesn't exist, or it moved.">
      <LinkButton href="/" variant="primary" className="w-full">
        Go to Spotter
      </LinkButton>
    </AuthCard>
  );
}
