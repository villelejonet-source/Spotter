import type { NextConfig } from "next";

const supabase = process.env.NEXT_PUBLIC_SUPABASE_URL ?? "";
const dev = process.env.NODE_ENV !== "production";

// One static policy for every route.
//
// Scripts keep 'unsafe-inline' rather than a per-request nonce: Next's nonce support only
// applies to dynamically rendered pages, and the sign-in, sign-up, forgot-password and
// reset-password pages are static client components. A nonce policy from the middleware
// would block their inline bootstrap scripts (prerendered without the nonce) and break
// sign-in. Revisit if those pages become dynamic. There is no third-party script, so the
// remaining risk is an injection in our own markup, which React's escaping already covers;
// object-src, base-uri, form-action and frame-ancestors are locked down regardless.
// Dev also needs eval for React Refresh.
const csp = [
  "default-src 'self'",
  `script-src 'self' 'unsafe-inline'${dev ? " 'unsafe-eval'" : ""}`,
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob:",
  "font-src 'self'",
  `connect-src 'self' ${supabase} ${supabase.replace(/^https/, "wss")}`,
  "frame-ancestors 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  "object-src 'none'",
  // Not in dev: it would rewrite http://localhost subresources to https.
  ...(dev ? [] : ["upgrade-insecure-requests"]),
].join("; ");

const nextConfig: NextConfig = {
  poweredByHeader: false,
  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          { key: "Content-Security-Policy", value: csp },
          { key: "X-Frame-Options", value: "DENY" },
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
          { key: "Strict-Transport-Security", value: "max-age=63072000; includeSubDomains" },
          { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
        ],
      },
    ];
  },
};

export default nextConfig;
