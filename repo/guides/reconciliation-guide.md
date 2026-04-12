# Settlement Import & Reconciliation Guide

## Overview
LearnMart supports importing settlement files (CSV or JSON) and reconciling them against recorded payments.

## Supported File Formats
- **CSV**: Comma-separated with headers matching the field mapping
- **JSON**: Array of objects with matching field names

## Required Fields
| Field | Description | Required |
|-------|-------------|----------|
| external_id | Unique row identifier from settlement provider | Recommended |
| payment_reference | Payment record ID or order reference | Required for matching |
| amount | Settlement amount in USD | Required |
| tender_type | CASH, CHECK, or EXTERNAL_CARD_TERMINAL_REFERENCE | Optional |
| status | CLEARED, PENDING, etc. | Optional |
| transaction_date | ISO-8601 timestamp | Optional |

## Import Process
1. **File Validation**: Size check (max 25MB), format validation, UTF-8 encoding
2. **Row Parsing**: Each row is validated for required fields, data types, date formats
3. **Deduplication**: Rows with duplicate `external_id` values are flagged
4. **Batch Creation**: Valid rows are stored as a settlement batch

## Reconciliation Process
1. **Matching**: Each settlement row is matched against payment records by `payment_reference`
2. **Amount Verification**: Matched pairs are checked for amount discrepancies
3. **Discrepancy Cases**: Created for unmatched rows, amount mismatches, and missing references
4. **Atomic Commit**: The reconciliation run, matches, and discrepancies are committed together

## Discrepancy Management
- **OPEN**: Newly detected discrepancy
- **INVESTIGATING**: Under review by finance staff
- **RESOLVED**: Resolution note provided by authorized user
- **CLOSED**: Final state

## Idempotency
Re-importing the same settlement rows (identified by `external_id`) will not create duplicate financial effects. Duplicate rows are flagged during import validation.

## Data Cleansing Rules
- Whitespace trimmed from all fields
- Casing normalized for tender_type and status fields
- Invalid dates rejected with row-level error
- Duplicate external IDs within same batch rejected

## Access Control
- **Import**: Requires `import.manage` permission (Admin, Finance Clerk)
- **Reconciliation**: Requires `payment.reconcile` permission (Admin, Finance Clerk)
- **Discrepancy Resolution**: Requires `payment.reconcile` permission
