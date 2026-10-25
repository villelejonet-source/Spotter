# Sign-in emails and auth settings (Supabase dashboard)

The app and the website share accounts and flows: sign up with email + password and
confirm the email, sign in with the password, reset it by email, change it from Profile.
Accounts made before passwords existed sign in with an emailed code and can set a password
with "Forgot password". Both use the same email templates, so every email carries **both**
a link and a code:

- The **link** (`{{ .ConfirmationURL }}`) works on the device that asked for it: it opens
  the app (`spotter://login`) or the website (`/auth/callback`), whichever sent the request.
- The **code** (`{{ .Token }}`) works anywhere: type it into the app or the website. Use it
  when the email is opened on another device or browser.

Keep `{{ .ConfirmationURL }}` as the link: the app's deep link depends on it.

## 1. Email templates

Supabase → **Authentication → Emails → Templates**. Replace the subject and body of each.

### Confirm signup

Sent when someone creates an account on the website, and the first time someone signs in
to the app with a new email.

Subject: `Confirm your Spotter account`

```html
<h2>Welcome to Spotter</h2>
<p>Confirm your email to finish setting up your account.</p>
<p><a href="{{ .ConfirmationURL }}">Confirm my email</a></p>
<p>Or enter this code: <strong style="font-size:20px;letter-spacing:4px">{{ .Token }}</strong></p>
<p style="color:#85736c;font-size:13px">The link and code work once and expire in an hour. If you didn't sign up for Spotter, ignore this email.</p>
```

### Magic Link

Sent for "Email me a sign-in code" on the website and for sign-in in the app.

Subject: `Your Spotter sign-in code: {{ .Token }}`

```html
<h2>Sign in to Spotter</h2>
<p>Your code: <strong style="font-size:20px;letter-spacing:4px">{{ .Token }}</strong></p>
<p>Or <a href="{{ .ConfirmationURL }}">sign in with this link</a> on the device you asked from.</p>
<p style="color:#85736c;font-size:13px">The link and code work once and expire in an hour. If you didn't ask to sign in, ignore this email.</p>
```

### Reset Password

Sent from "Forgot password?" on the website. Also how an account made in the app gets its
first password.

Subject: `Reset your Spotter password`

```html
<h2>Reset your password</h2>
<p><a href="{{ .ConfirmationURL }}">Choose a new password</a></p>
<p>Or enter this code on the reset page: <strong style="font-size:20px;letter-spacing:4px">{{ .Token }}</strong></p>
<p style="color:#85736c;font-size:13px">The link and code work once and expire in an hour. If you didn't ask for this, ignore this email; your password stays the same.</p>
```

### Reauthentication

Sent when someone changes their password a while after signing in (see "Secure password
change" below).

Subject: `Confirm your Spotter password change`

```html
<h2>Confirm your password change</h2>
<p>Enter this code to confirm: <strong style="font-size:20px;letter-spacing:4px">{{ .Token }}</strong></p>
<p style="color:#85736c;font-size:13px">If you didn't try to change your password, sign in and change it now.</p>
```

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
- **Redirect URLs:** `spotter://login` (the app), `https://spotter.northstarsolutions.se/auth/callback`
  (the website) and `http://localhost:3000/auth/callback` (local development).
  The reset link uses `/auth/callback?next=/reset-password`; a redirect URL matches with or
  without a query string.

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
| Sign up | `/signup` → "Confirm your email" | Confirm signup | Code or link confirms and signs in → dashboard |
| Sign in | `/login` with email + password | — | Dashboard. Unconfirmed accounts get a new confirmation email and the code step |
| Sign in without a password | `/login` → "Email me a sign-in code" | Magic Link | Code or link → dashboard. Only for existing accounts |
| Forgot password | `/forgot-password` → "Check your email" | Reset Password | Code or link → `/reset-password` → new password saved |
| Change password | Profile → Account → Change password | Reauthentication (sometimes) | Saved |

Messages never reveal whether an email has an account, except at sign-up, where Supabase
reports that the address is taken.
