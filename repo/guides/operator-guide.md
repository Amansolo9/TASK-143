# LearnMart Operator / Admin Guide

## First Launch

On first launch, the app seeds the following demo accounts:

| Username | Password | Role |
|----------|----------|------|
| admin | admin1234 | Administrator |
| registrar | pass1234 | Registrar |
| instructor | pass1234 | Instructor |
| ta | pass1234 | Teaching Assistant |
| learner | pass1234 | Learner |
| finance | pass1234 | Finance Clerk |

**Change all default passwords after first login.**

## Login

1. Launch the app
2. Enter username and password
3. Tap "Login"

After 5 failed login attempts within 15 minutes, the account is locked for 15 minutes. An administrator can manually unlock accounts from the User Management screen.

## Session Timeout

Sessions expire after 15 minutes of idle time (configurable via the `session_timeout_minutes` system policy). Sensitive operations (refunds, restore, policy edits, blacklist changes) require re-authentication.

## User Management (Administrator Only)

- Navigate to Dashboard > User Management
- Create new users with username, display name, credential, and role assignment
- View user details, unlock locked accounts, disable accounts
- Assign additional roles to users

## Policy Management (Administrator Only)

- Navigate to Dashboard > Policies
- Filter by policy type (System, Enrollment, Commerce, Tax, Fee, Risk, Backup, Import Mapping)
- Tap a policy to edit its value
- All policy changes are versioned and audited with optional reason notes

### Key Policies

| Policy | Type | Default | Description |
|--------|------|---------|-------------|
| session_timeout_minutes | SYSTEM | 15 | Idle session timeout |
| lockout_attempts | SYSTEM | 5 | Failed logins before lockout |
| lockout_duration_minutes | SYSTEM | 15 | Lockout duration |
| minimum_order_total | COMMERCE | 25.00 | Min order amount (USD) |
| packaging_fee | COMMERCE | 1.50 | Default packaging fee |
| max_refunds_per_learner_per_day | RISK | 3 | Daily refund limit |

## Audit Log (Administrator, Registrar)

- Navigate to Dashboard > Audit Log
- View all system events with actor, action, target, timestamp, and outcome
- Scroll for paginated loading of historical events
- Refresh to see latest events

## Backup & Restore (Administrator Only)

- Navigate to Dashboard > Operations > Backups
- **Create Backup**: Enqueues a WorkManager job that runs when the device is idle and charging. The backup is AES-256-GCM encrypted using a key derived from the operator-configured passphrase (PBKDF2, 120K iterations).
- **Export to File**: Uses the system file picker (SAF) to save a backup archive to any accessible storage location.
- **Import & Restore from File**: Uses the system file picker to select a `.enc` backup archive for restore.
- **Restore from List**: Restores from an existing on-device backup archive.
- **Important**: Set the `backup_passphrase` policy before creating backups. Without it, backup creation fails.

## Data Security

- Database is encrypted at rest using SQLCipher
- No data is transmitted over the network
- The app operates fully offline
- Backup archives use AES-256-GCM with PBKDF2-derived keys (not Keystore-backed)
- Operations routes are guarded at both the navigation and use-case layers

## Troubleshooting

**Locked out?** Wait 15 minutes for automatic unlock, or have another administrator unlock the account.

**App data reset?** The database is encrypted. If the encryption key is lost (e.g., app data cleared), a restore from backup is required.

**Session expired?** Re-login. The session timeout is configurable via system policies.
