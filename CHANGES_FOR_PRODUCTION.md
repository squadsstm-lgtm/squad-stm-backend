# Changes For Production (Backend)

Use this checklist before go-live.

## 1) Security and Auth Settings

Update these values in your production environment (preferred) or production properties:

- `app.cookie-secure=true`
- `app.cors-origins=https://YOUR_FRONTEND_DOMAIN`
- `app.frontend-url=https://YOUR_FRONTEND_DOMAIN`
- `app.backend-url=https://YOUR_API_DOMAIN/api`
- `server.forward-headers-strategy=framework`

Notes:
- `app.cookie-secure=true` is required for secure auth cookies on HTTPS.
- `app.cors-origins` can be comma-separated if needed:
  - `https://app.example.com,https://admin.example.com`

## 2) JWT and Secret Keys

Replace weak/default values:

- `jwt.secret` -> strong random secret (minimum 64+ chars)
- `app.encryption.key` -> production AES key from secure secret manager

Do not keep these in git for production.

## 3) Database and Service Credentials

Move all sensitive values to secrets/environment variables:

- `spring.data.mongodb.uri`
- `stripe.secret-key`
- `stripe.webhook-secret`
- `spring.mail.username`
- `spring.mail.password`
- Firebase service account key

## 4) HTTPS and Domain

Make sure deployment is behind HTTPS (AWS ALB/CloudFront/Nginx).

- API must be reachable as `https://...`
- Frontend must call API using `https://...`
- Cookies will then be sent securely.

## 5) CORS Validation

After deployment, verify:

- Preflight requests (`OPTIONS`) succeed from frontend domain.
- Auth calls include credentials.
- No wildcard origin is used with credentials.

## 6) Firebase/Google Auth

In Firebase Console, ensure authorized domains include your production frontend domain.

Frontend and backend must use the same Firebase project.

## 7) Post-Deploy Smoke Tests

Run these after go-live:

1. Email/password login works.
2. Google login works.
3. Refresh works silently (no forced logout while active).
4. Idle timeout logs out after configured inactivity.
5. Closing browser requires login again.
6. Logout clears session and refresh cookie.

