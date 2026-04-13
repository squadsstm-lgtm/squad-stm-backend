# Postman Collection for Squad Backend API

## Quick Start

1. **Import Collection:**
   - Open Postman
   - Click "Import" button
   - Select `Squad-Backend-API.postman_collection.json`
   - Click "Import"

2. **Import Environment (Optional but Recommended):**
   - Click "Import" again
   - Select `Squad-Backend-Environment.postman_environment.json`
   - Click "Import"
   - Select "Squad Backend - Local" from the environment dropdown (top right)

3. **Start Testing:**
   - Make sure your Spring Boot backend is running on `http://localhost:8080`
   - Start with **Auth → Login** or **Auth → Signup** to get a token
   - The token will be automatically saved to the environment after login

## Collection Structure

- **Auth** - Authentication endpoints (signup, login, Google login, etc.)
- **Players** - Player management (CRUD, bulk create, check availability)
- **Teams** - Team management (CRUD)
- **Sessions** - Session management (CRUD, attendance)
- **Payments** - Stripe payment integration
- **Confirmation Requests** - Session confirmation and payment requests
- **Users** - User management
- **Roles** - Role management
- **Finance** - Wallet, transactions, financial summary
- **Seasons** - Season management
- **Counts** - Statistics and counts

## Important Notes

### Authentication
- Most endpoints require authentication
- Use **Auth → Login** first to get an `accessToken`
- The token is automatically saved after login (see the "test" script in Login request)
- Token is valid for 24 hours (as per JWT expiration)

### Environment Variables
The collection uses these variables:
- `baseUrl` - Backend URL (default: `http://localhost:8080`)
- `accessToken` - JWT token (auto-saved after login)
- `refreshToken` - Refresh token (auto-saved after login)
- `userId` - Current user ID (auto-saved after login)
- `clubId` - Current club ID (auto-saved after login)

### Fake Data
All requests include sample/fake data. Replace:
- `PLAYER_ID_HERE` - With actual player ID from database
- `SESSION_ID_HERE` - With actual session ID from database
- `TEAM_ID_HERE` - With actual team ID from database
- `ROLE_ID_HERE` - With actual role ID from database
- `REQUEST_ID_HERE` - With actual confirmation request ID
- `TRANSACTION_ID_HERE` - With actual transaction ID

### Testing Flow

1. **First Time Setup:**
   ```
   Auth → Signup (create a new account)
   Auth → Login (get token)
   ```

2. **Create Data:**
   ```
   Teams → Create Team
   Players → Create Player
   Sessions → Create Session
   ```

3. **Test Features:**
   ```
   Confirmation Requests → Get All Requests
   Payments → Create Payment Intent
   Finance → Get Wallet
   ```

## Stripe Testing

For Stripe payment endpoints:
- Use Stripe test mode (already configured)
- Use test card numbers from Stripe documentation:
  - Success: `4242 4242 4242 4242`
  - Decline: `4000 0000 0000 0002`
  - Any future expiry date and any CVC

## Firebase Testing

For Google login:
- You need a valid Firebase ID token
- Get it from your frontend after Google Sign-In
- Replace `YOUR_FIREBASE_ID_TOKEN_HERE` in the request body

## Troubleshooting

### "Unauthorized" Error
- Make sure you've logged in first
- Check if token is expired (login again)
- Verify token is in the Authorization header

### "Not Found" Errors
- Make sure you're using correct IDs from your database
- Create the resource first (e.g., create team before creating player with teamId)

### Connection Errors
- Verify backend is running on `http://localhost:8080`
- Check `baseUrl` in environment variables
- Check firewall/network settings

## Tips

1. **Use Collection Runner:**
   - Select multiple requests
   - Click "Run" to execute them in sequence
   - Useful for testing complete flows

2. **Save Responses:**
   - Right-click on response → "Save Response" → "Save as Example"
   - Useful for documentation

3. **Use Pre-request Scripts:**
   - Add scripts to set variables before requests
   - Useful for dynamic data generation
