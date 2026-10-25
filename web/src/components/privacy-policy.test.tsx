import { readFileSync } from "node:fs";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { PrivacyPolicy } from "./privacy-policy";

/** Text as React writes it into HTML. */
const escape = (text: string) => text.replaceAll("&", "&amp;").replaceAll("'", "&#x27;").replaceAll('"', "&quot;");

describe("PrivacyPolicy", () => {
  it("renders the policy from docs/play-store, as copied in", () => {
    const source = readFileSync(new URL("../../../docs/play-store/privacy-policy.md", import.meta.url), "utf8");
    const html = renderToStaticMarkup(<PrivacyPolicy />);
    expect(html).toContain(">Spotter privacy policy</h1>");
    // Every section's bold lead-in, straight from the source file.
    const leads = [...source.matchAll(/\*\*(.+?)\*\*/g)].map((m) => m[1]);
    expect(leads.length).toBeGreaterThan(5);
    for (const lead of leads) expect(html).toContain(`>${escape(lead)}</strong>`);
    expect(html).toContain("<ol");
  });

  it("renders the markdown it uses: headings, emphasis, lists and links", () => {
    const html = renderToStaticMarkup(<PrivacyPolicy markdown={"# Title\n\n_Dated_\n\n**Lead.** Text with [a link](https://example.com).\n\n- one\n- two"} />);
    expect(html).toContain(">Title</h1>");
    expect(html).toContain("<em>Dated</em>");
    expect(html).toContain('href="https://example.com"');
    expect(html).toMatch(/<ul[^>]*>\s*<li>one<\/li>/);
  });
});
