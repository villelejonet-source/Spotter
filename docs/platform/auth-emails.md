# Sign-in emails and auth settings (Supabase dashboard)

The app and the website share accounts and flows. The way in for everyone is an emailed
code: type an email, then the code. An address without an account gets one, so there's no
separate sign-up, and the code also confirms an account that never was. A password is
optional: people who set one (in the app, or on the Profile page) can still sign in with
it, and reset it by email. Every email carries **both** a link and a code:

- The **link** (`{{ .ConfirmationURL }}`) works on the device that asked for it: it opens
  the app (`spotter://login`) or the website (`/auth/callback`), whichever sent the request.
- The **code** (`{{ .Token }}`) works anywhere: type it into the app or the website. Use it
  when the email is opened on another device or browser.

Keep `{{ .ConfirmationURL }}` as the link: the app's deep link depends on it.

## 1. Email templates

The templates live in `supabase/templates/`, and `supabase/config.toml` sets their subjects,
so the local stack sends the same emails as production. Production keeps its own copy:
after changing a template, paste it into Supabase → **Authentication → Emails → Templates**
(after asking, since it's production).

| Template | File | Subject | Sent when |
|---|---|---|---|
| Confirm signup | `confirmation.html` | `Your Spotter code: {{ .Token }}` | The first code for a new address (it makes the account) |
| Magic Link | `magic_link.html` | `Your Spotter code: {{ .Token }}` | Every other code: app and website sign-in |
| Reset Password | `recovery.html` | `Reset your Spotter password` | "Forgot password?" |
| Reauthentication | `reauthentication.html` | `Confirm your Spotter password change` | Changing the password a while after signing in |

The code is in the subject, so it shows in the notification and people rarely need to open
the email at all.

## 2. Settings

**Authentication → Sign In / Providers → Email**
- Enable email provider: on. **Confirm email: on** (already on).
- **Secure password change: on.** Changing the password needs a recent sign-in or an emailed
  code; the website handles both.
- **Minimum password length: 8** (the website asks for 8 too). Password requirements:
  "letters and digits" is a reasonable minimum.
- Email OTP length: 6. Email OTP expiration: 3600 seconds.

**Authentication → URL Configuration**
- **Site URL:** `https://spotter.northstarsolutions.se`. Supabase falls back to it when a
  request names no redirect, so it must be the live website.
- **Redirect URLs:** `spotter://login` (the app), `https://spotter.northstarsolutions.se/auth/callback**`
  (the website) and `http://localhost:3000/auth/callback**` (local development). The `**`
  lets the link carry where to go next (`/auth/callback?next=/history`, or
  `?next=/reset-password` for a reset): Supabase matches redirect URLs exactly otherwise, and
  falls back to the Site URL, which loses the `next`.

**Authentication → Rate Limits**: defaults are fine. With custom SMTP, emails are limited per
hour; raise it only if real users hit it. Google Workspace itself allows about 2 000 sent
emails a day per user.

## 3. SMTP sender

Auth emails come from **`Spotter <noreply@northstarsolutions.se>`**, sent through the
company's Google Workspace, so they pass SPF (`include:_spf.google.com`) and are DKIM-signed
by `northstarsolutions.se` (`google._domainkey`); DMARC is `p=none` for now. (Until
2026-10-25 they were sent through a personal Gmail account.)

`noreply@` is an alias on the Workspace user, not a separate mailbox: replies still land in
that user's inbox. Set up once:

1. **Google Admin → Directory → Users →** the user **→ User information → Alternate email
   addresses:** add `noreply`.
2. **Gmail** (as that user) **→ Settings → See all settings → Accounts → Send mail as → Add
   another email address:** name `Spotter`, the alias, "Treat as an alias" ticked. Without
   this, Gmail rewrites the sender to the login address.
3. **myaccount.google.com/apppasswords** (needs 2-Step Verification): create an app password
   named `Supabase Spotter`.
4. **Supabase → Authentication → Emails → SMTP Settings:** host `smtp.gmail.com`, port
   `465`, username = the Workspace login, password = the app password, sender email
   `noreply@northstarsolutions.se`, sender name `Spotter`.

To rotate: create a new app password, paste it into Supabase, send a test email, then revoke
the old one. In a received email, Gmail's **⋮ → Show original** should show SPF and DKIM
**PASS** and "signed-by: northstarsolutions.se".

## How each flow works

The app (Profile → Backup & sync, or the welcome screen) and the website have the same
steps. In the app a reset link opens "New password" directly.

| Flow | Website pages | Email | Ends |
|---|---|---|---|
| Sign in or sign up | `/login`: email → "Check your email" | Confirm signup (new address) or Magic Link | Code or link → the page they wanted, or the dashboard |
| Sign in with a password | `/login` → "Use a password instead" | — | The page they wanted. Unconfirmed accounts get a code, which confirms them |
| Forgot password | `/forgot-password` → "Check your email" | Reset Password | Code or link → `/reset-password` → new password saved |
| Change or set a password | Profile → Account → Change password | Reauthentication (sometimes) | Saved |

`/signup` forwards to `/login`. A signed-out visitor sent to `/login` keeps the page they
asked for in `?next=`, and signing in (by code, link or password) lands there.

Typing an email doesn't say whether it has an account: either way the next step is "We sent
a code".
