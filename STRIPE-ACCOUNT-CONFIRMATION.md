# Stripe Account Confirmation ✅

## Good News: You're Already Using the Same Stripe Account!

### ✅ Current Configuration

**Java Backend (`application.properties`):**
```properties
stripe.secret-key=sk_test_XXXX   # see application.properties (gitignored)
stripe.publishable-key=pk_test_XXXX
```

**Node.js Backend (`config.js`):**
```javascript
STRIPE_SECRET_KEY: process.env.STRIPE_SECRET_KEY  // Same key
STRIPE_PUBLISHABLE_KEY: process.env.STRIPE_PUBLISHABLE_KEY  // Same key
```

**✅ Both backends use the SAME Stripe account!**

---

## What is the Webhook Secret?

The **webhook secret** is NOT an API key. It's a separate security token that:

1. **Verifies webhook requests** - Ensures requests are actually from Stripe
2. **Unique per webhook endpoint** - Each webhook you create has its own secret
3. **Different from API keys** - API keys identify your account, webhook secret verifies webhook authenticity

### Think of it like this:
- **API Keys** = Your Stripe account credentials (who you are)
- **Webhook Secret** = A password to verify webhook requests (security check)

---

## How to Get Your Webhook Secret

### Step 1: Go to Stripe Dashboard
1. Visit: https://dashboard.stripe.com/
2. Make sure you're logged into the **same account** that has these API keys

### Step 2: Navigate to Webhooks
1. Click **Developers** in the left menu
2. Click **Webhooks**

### Step 3: Find or Create Webhook
**If you already have a webhook:**
- Click on the webhook endpoint
- Look for **Signing secret** section
- Click **Reveal** or **Copy**
- Copy the secret (starts with `whsec_...`)

**If you don't have a webhook yet:**
1. Click **Add endpoint**
2. Enter endpoint URL:
   - **For production:** `https://your-domain.com/api/payments/webhook`
   - **For local testing:** Use Stripe CLI (see below)
3. Select events: `checkout.session.completed`
4. Click **Add endpoint**
5. Copy the **Signing secret**

### Step 4: Update Java Backend
Update `application.properties`:
```properties
stripe.webhook-secret=whsec_YOUR_ACTUAL_SECRET_HERE
```

---

## Testing Locally (Without Stripe Dashboard)

If you want to test locally without setting up a webhook in Stripe Dashboard:

### Use Stripe CLI:

1. **Install Stripe CLI:**
   - Download from: https://github.com/stripe/stripe-cli/releases
   - Or use: `scoop install stripe` (if you have Scoop)

2. **Login:**
   ```bash
   stripe login
   ```

3. **Forward webhooks to local server:**
   ```bash
   stripe listen --forward-to localhost:8080/api/payments/webhook
   ```
   
   This will give you a webhook secret like: `whsec_xxxxx`

4. **Use that secret in `application.properties`** for local testing

5. **Test webhook:**
   ```bash
   stripe trigger checkout.session.completed
   ```

---

## Summary

✅ **Same Stripe Account:** Yes, you're already using the same account (same API keys)  
⚠️ **Webhook Secret:** This is just a security token - get it from Stripe Dashboard  
📝 **Action Needed:** Copy webhook secret from Stripe Dashboard → Update `application.properties`

**The webhook secret doesn't change which Stripe account you're using - it's just for security verification!**
