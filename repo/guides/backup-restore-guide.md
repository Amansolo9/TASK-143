# Backup & Restore Guide

## Overview
LearnMart supports encrypted backups and restores, operating fully offline through scoped storage.

## Prerequisites
**The administrator MUST configure the `backup_passphrase` policy before creating backups.** Backup creation fails closed when no passphrase is set — there is no default fallback. Set this via Policies > BACKUP > backup_passphrase.

## Backup Process
1. Navigate to Operations > Backups
2. Tap "Create Backup"
3. The system derives an AES-256 key from the configured passphrase via PBKDF2 (120K iterations) and creates an encrypted archive containing:
   - Schema version number
   - Backup timestamp
   - Checksum manifest (SHA-256)
   - App version
   - Encrypted database payload

## Backup Archive Contents
| Component | Description |
|-----------|-------------|
| Schema Version | Database schema version at time of backup |
| App Version | Application version that created the backup |
| Timestamp | UTC timestamp of backup creation |
| Checksum | SHA-256 hash for integrity verification |
| Encryption | AES-256-GCM with key derived via PBKDF2WithHmacSHA256 from operator passphrase |

## Restore Process
1. Navigate to Operations > Backups
2. Select a backup archive (must be SUCCEEDED or VERIFIED)
3. Tap "Restore"
4. The system validates:
   - Archive integrity (checksum verification)
   - Schema compatibility (version must be <= current)
5. On success: full replace of local dataset
6. A complete audit record is created for the restore

## Encryption Model

The backup secret is an operator-configured passphrase stored as the `backup_passphrase` policy value. It is NOT stored in Android Keystore — Keystore is used only for the primary database encryption key, not for backup archives.

**Key derivation**: PBKDF2WithHmacSHA256 with 120,000 iterations derives a 256-bit AES key from the passphrase + a random 32-byte salt.

**Archive format**: `[salt_len:1][salt:32][iv_len:1][iv:12][AES-GCM encrypted payload]`

- The archive header stores ONLY the salt and IV. No raw key material is written.
- On restore, the operator must provide the same passphrase. The key is re-derived from passphrase + stored salt.
- A wrong passphrase causes AES-GCM authentication to fail (AEADBadTagException), preventing silent data corruption.

**What operators must provision**: Set the `backup_passphrase` policy via Policies > BACKUP before the first backup. Store this passphrase securely offline. Without it, encrypted archives cannot be restored.

## Scoped Storage (Android 10+)

Backup export and restore-from-file use the Storage Access Framework (SAF):
- **Export**: Uses `CreateDocument` to let the operator choose a save location via the system file picker.
- **Import/Restore**: Uses `OpenDocument` to let the operator select an `.enc` archive from any accessible storage provider.

Content URIs from SAF are passed to `BackupRestoreUseCase.exportBackupToStream()` and `restoreFromStream()` respectively. No direct file-path access is used for external files.

## Important Notes
- **Admin-only**: Both backup and restore require Administrator role
- **Re-authentication**: Required before restore operations
- **No network**: Backup/restore operates entirely offline
- **Incompatible schemas**: Restore fails with explicit message if schema version is incompatible
- **Audit trail**: Every backup and restore creates audit events
- **WorkManager**: Backup creation runs as a WorkManager job constrained to idle+charging

## Backup States
| State | Description |
|-------|-------------|
| REQUESTED | Backup initiated |
| RUNNING | Backup in progress |
| SUCCEEDED | Backup completed successfully |
| FAILED | Backup failed (retryable) |
| VERIFIED | Integrity verified post-creation |

## Troubleshooting
- **Backup fails**: Check available storage space. Retry from the backup list.
- **Restore fails integrity check**: The archive may be corrupted. Use a different backup.
- **Schema mismatch**: Update the app before restoring from a newer schema version.
