"use client";

import { useEffect } from "react";

/**
 * The last resort when the root layout itself fails: no styles are guaranteed to have
 * loaded, so this uses plain inline styles and the app's colours by hand.
 */
export default function GlobalError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <html lang="en">
      <body style={{ margin: 0, fontFamily: "system-ui, sans-serif", background: "#fff8f6", color: "#231a16" }}>
        <main style={{ maxWidth: 420, margin: "15vh auto", padding: 24, textAlign: "center" }}>
          <h1 style={{ fontSize: 20, margin: "0 0 8px" }}>Something went wrong</h1>
          <p style={{ color: "#53433e", fontSize: 14, margin: "0 0 20px" }}>Spotter couldn&apos;t load. Try again in a moment.</p>
          <button
            type="button"
            onClick={reset}
            style={{ background: "#9a4521", color: "#fff", border: 0, borderRadius: 999, padding: "10px 20px", fontSize: 14, cursor: "pointer" }}
          >
            Try again
          </button>
        </main>
      </body>
    </html>
  );
}
