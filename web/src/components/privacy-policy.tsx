import Markdown, { type Components } from "react-markdown";
// Relative, not "@/data/…": vitest has no path aliases configured (see middleware.test.ts).
import policy from "../data/privacy-policy.json";

// The policy's markdown in the site's type: the same sizes and colours as a page and its cards.
const components: Components = {
  h1: (props) => <h1 className="text-2xl font-semibold tracking-tight" {...props} />,
  h2: (props) => <h2 className="mt-8 text-lg font-semibold" {...props} />,
  h3: (props) => <h3 className="mt-6 font-semibold" {...props} />,
  p: (props) => <p className="mt-4 leading-relaxed" {...props} />,
  strong: (props) => <strong className="font-semibold text-foreground" {...props} />,
  a: (props) => <a className="rounded-md font-medium text-primary underline underline-offset-2" {...props} />,
  ol: (props) => <ol className="mt-4 list-decimal space-y-2 pl-6 leading-relaxed" {...props} />,
  ul: (props) => <ul className="mt-4 list-disc space-y-2 pl-6 leading-relaxed" {...props} />,
};

/** docs/play-store/privacy-policy.md, copied in by scripts/copy-shared.mjs. */
export function PrivacyPolicy({ markdown = policy.markdown }: { markdown?: string }) {
  return (
    <article className="text-[0.9375rem]">
      <Markdown components={components}>{markdown}</Markdown>
    </article>
  );
}
