# Stripe Webhook Secret Configuration Guide

## Current Status

✅ **Stripe API Keys are Already Configured:**
- Your Java backend already has the same Stripe keys as Node.js:
  - `stripe.secret-key` = Same as Node.js `STRIPE_SECRET_KEY`
  - `stripe.publishable-key` = Same as Node.js `STRIPE_PUBLISHABLE_KEY`

⚠️ **Webhook Secret Needs Configuration:**
- Currently set to placeholder: `your-webhook-secret-from-stripe-dashboard`
- This needs to be the actual webhook secret from your Stripe Dashboard

---

## What is a Webhook Secret?

The **webhook secret** is a security token that Stripe generates when you create a webhook endpoint. It's used to verify that webhook requests are actually coming from Stripe and not from malicious sources.

**Important:** This is different from your API keys. It's a separate secret that's unique to each webhook endpoint you create.

---

## How to Get Your Webhook Secret

### Option 1: If You Already Have a Webhook in Stripe Dashboard

1. Go to [Stripe Dashboard](https://dashboard.stripe.com/)
2. Navigate to **Developers** → **Webhooks**
3. Find your webhook endpoint (the one pointing to your backend)
4. Click on the webhook to view details
5. In the **Signing secret** section, click **Reveal** or **Copy**
6. Copy the secret (it starts with `whsec_...`)

### Option 2: Create a New Webhook Endpoint

If you don't have a webhook yet, create one:

1. Go to [Stripe Dashboard](https://dashboard.stripe.com/)
2. Navigate to **Developers** → **Webhooks**
3. Click **Add endpoint**
4. Enter your webhook URL:
   - **For local testing:** Use Stripe CLI (see below)
   - **For production:** `https://your-domain.com/api/payments/webhook`
5. Select events to listen to:
   - ✅ `checkout.session.completed`
   - ✅ `payment_intent.succeeded` (optional)
6. Click **Add endpoint**
7. Copy the **Signing secret** (starts with `whsec_...`)

---

## Where to Configure in Java Backend

### Update `application.properties`

```properties
# Stripe – copy real keys from application.properties (gitignored)
stripe.secret-key=sk_test_XXXX
stripe.publishable-key=pk_test_XXXX
stripe.webhook-secret=whsec_YOUR_ACTUAL_WEBHOOK_SECRET_HERE
```

Replace `whsec_YOUR_ACTUAL_WEBHOOK_SECRET_HERE` with the actual secret from Stripe Dashboard.

---

## Testing Webhooks Locally

### Using Stripe CLI (Recommended for Development)

1. **Install Stripe CLI:**
   ```bash
   # Windows (using Scoop or download from GitHub)
   # Or download from: https://github.com/stripe/stripe-cli/releases
   ```

2. **Login to Stripe:**
   ```bash
   stripe login
   ```

3. **Forward webhooks to your local server:**
   ```bash
   stripe listen --forward-to localhost:8080/api/payments/webhook
   ```
   
   This will:
   - Create a temporary webhook endpoint
   - Give you a webhook secret (starts with `whsec_`)
   - Forward all Stripe events to your local server

4. **Use the webhook secret from CLI:**
   - The CLI will show you a webhook secret like: `whsec_xxxxx`
   - Use this in your `application.properties` for local testing

5. **Trigger test events:**
   ```bash
   stripe trigger checkout.session.completed
   ```

---

## Important Notes

1. **Different Secrets for Different Environments:**
   - **Local/Development:** Use webhook secret from Stripe CLI
   - **Production:** Use webhook secret from Stripe Dashboard webhook endpoint

2. **Security:**
   - Never commit webhook secrets to Git
   - Use environment variables in production
   - The webhook secret verifies that requests are from Stripe

3. **Current Implementation:**
   - The Java backend reads webhook secret from `application.properties`
   - It falls back to placeholder if not found (for development)
   - In production, you should use environment variables

---

## Quick Setup Steps

1. ✅ **Stripe API Keys:** Already configured (same as Node.js)
2. ⚠️ **Webhook Secret:** Get from Stripe Dashboard or Stripe CLI
3. 📝 **Update:** Replace placeholder in `application.properties`
4. ✅ **Test:** Use Stripe CLI to test webhook locally

---

## Summary

- **Same Stripe Account:** ✅ Yes, you're using the same API keys
- **Webhook Secret:** ⚠️ Needs to be obtained from Stripe Dashboard
- **Configuration:** Update `stripe.webhook-secret` in `application.properties`
- **Testing:** Use Stripe CLI for local webhook testing

The webhook secret is just a security token - it doesn't change which Stripe account you're using. Your API keys already connect to the same Stripe account as your Node.js backend.
