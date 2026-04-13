# Webhook Secret Setup Guide

## Current Status

✅ **Both Backends Use Same Stripe Account:**
- Node.js `.env`: `STRIPE_SECRET_KEY` = `sk_test_XXXX` (see .env, gitignored)
- Java `application.properties`: `stripe.secret-key` = Same key (see application.properties, gitignored) ✅

⚠️ **Both Backends Need Webhook Secret:**
- Node.js `.env`: `STRIPE_WEBHOOK_SECRET=your-webhook-secret-from-stripe-dashboard` (placeholder)
- Java `application.properties`: `stripe.webhook-secret=your-webhook-secret-from-stripe-dashboard` (placeholder)

---

## What You Need to Do

### Step 1: Get Webhook Secret from Stripe Dashboard

1. **Go to Stripe Dashboard:**
   - Visit: https://dashboard.stripe.com/
   - Make sure you're logged into the account with these API keys

2. **Navigate to Webhooks:**
   - Click **Developers** (left sidebar)
   - Click **Webhooks**

3. **Check if Webhook Exists:**
   - Look for any existing webhook endpoints
   - If you see one pointing to your backend URL, click on it
   - If no webhook exists, you need to create one (see Step 2)

4. **Get the Signing Secret:**
   - Click on the webhook endpoint
   - Find **Signing secret** section
   - Click **Reveal** or **Copy**
   - Copy the secret (it starts with `whsec_...`)

---

### Step 2: Create Webhook (If It Doesn't Exist)

If you don't have a webhook endpoint yet:

1. In Stripe Dashboard → Developers → Webhooks
2. Click **Add endpoint**
3. **Endpoint URL:**
   - **For Production:** `https://your-domain.com/api/payments/webhook`
   - **For Local Testing:** Use Stripe CLI (see below)
4. **Description:** "Squad STM Payment Webhooks"
5. **Events to send:**
   - ✅ `checkout.session.completed`
   - ✅ `payment_intent.succeeded` (optional)
6. Click **Add endpoint**
7. **Copy the Signing secret** (starts with `whsec_...`)

---

### Step 3: Update Both Backends

#### Node.js Backend (`.env` file):
```env
STRIPE_WEBHOOK_SECRET=whsec_YOUR_ACTUAL_SECRET_HERE
```

#### Java Backend (`application.properties`):
```properties
stripe.webhook-secret=whsec_YOUR_ACTUAL_SECRET_HERE
```

**Replace `whsec_YOUR_ACTUAL_SECRET_HERE` with the actual secret from Stripe Dashboard.**

---

## Testing Locally (Alternative Method)

If you want to test locally without creating a webhook in Stripe Dashboard:

### Use Stripe CLI:

1. **Install Stripe CLI:**
   ```bash
   # Windows - Download from: https://github.com/stripe/stripe-cli/releases
   # Or use: scoop install stripe
   ```

2. **Login:**
   ```bash
   stripe login
   ```

3. **Forward webhooks to your local server:**
   ```bash
   # For Node.js (port 3000)
   stripe listen --forward-to localhost:3000/api/payments/webhook
   
   # For Java (port 8080)
   stripe listen --forward-to localhost:8080/api/payments/webhook
   ```

4. **The CLI will show you a webhook secret:**
   ```
   > Ready! Your webhook signing secret is whsec_xxxxx (^C to quit)
   ```

5. **Use that secret in both `.env` and `application.properties`** for local testing

6. **Test webhook:**
   ```bash
   stripe trigger checkout.session.completed
   ```

---

## Important Notes

1. **Different Secrets for Different Environments:**
   - **Local Development:** Use webhook secret from Stripe CLI
   - **Production:** Use webhook secret from Stripe Dashboard webhook endpoint

2. **Same Secret for Both Backends:**
   - If both backends point to the same webhook URL, they use the same secret
   - If they have different webhook URLs, they need different secrets

3. **Security:**
   - Never commit webhook secrets to Git
   - Use environment variables or secure config files
   - The webhook secret verifies that requests are from Stripe

---

## Quick Checklist

- [ ] Go to Stripe Dashboard → Developers → Webhooks
- [ ] Check if webhook endpoint exists
- [ ] If not, create webhook endpoint
- [ ] Copy the Signing secret (starts with `whsec_...`)
- [ ] Update Node.js `.env`: `STRIPE_WEBHOOK_SECRET=whsec_...`
- [ ] Update Java `application.properties`: `stripe.webhook-secret=whsec_...`
- [ ] Test webhook (use Stripe CLI for local testing)

---

## Summary

✅ **Same Stripe Account:** Both backends use the same API keys  
⚠️ **Webhook Secret:** Both need the actual secret from Stripe Dashboard  
📝 **Action:** Get secret from Stripe Dashboard → Update both `.env` and `application.properties`

The webhook secret is just for security verification - it doesn't change which Stripe account you're using!
