# Changelog

All notable changes to Hishab Ledger are documented in this file.

## [1.1.1] - Unreleased

### Added
- Show the transaction date separately from the entry timestamp.
- Add configurable app-lock grace periods: immediate, 30 seconds, 1 minute, and 5 minutes.
- Add text statements with up to 15 recent entries and PDF statements containing the full account history.

### Improved
- Precompute ledger balances when data changes and perform normal saves in the background.
- Warn about accounts with matching names or phone numbers.
- Configure release builds with code minification and resource shrinking.

### Security and recovery
- Support resetting the app PIN with the device screen-lock credential where available.
- Document that PIN recovery without a device screen lock may erase local ledger data.

## [1.1.0] - Unreleased

> Add the verified changes for version 1.1.0 here before publishing this changelog. The available project notes do not provide a complete, reliable list of changes specific to this version.

## [1.0.0] - Initial release

- Initial public release of Hishab Ledger.
- Track personal dues, receivables, and payments with an offline-first ledger.
- Store ledger data locally on the device.
- Provide account and transaction history, backup/restore, and statement-sharing features.
