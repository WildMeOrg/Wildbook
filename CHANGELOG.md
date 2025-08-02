# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased] - 2025-07-31

### Added
- `ANALYSIS.md`: project overview, build instructions, and improvement suggestions.
- `devops/development/deploy-backend.sh`: script to build and deploy the Wildbook WAR automatically.
- `devops/development/run-wildbook.sh`: comprehensive script to run Wildbook in Docker development environment with automatic port conflict resolution.

### Fixed
- Corrected JUnit 5 test execution by upgrading the `maven-surefire-plugin` and fixing misconfigured test files.
- Fixed Docker Compose environment variable syntax in `docker-compose.yml` for better compatibility.
- Added missing `ES_THRESHOLD` environment variable to `.env` template.
- Improved error handling and logging in deployment scripts.
- Made `run-wildbook.sh` more robust by adding a timeout when stopping existing Docker containers, preventing the script from hanging.

### Changed
- Updated `devops/README.md` to include `deploy-backend.sh` as an alternative to manual WAR extraction and deployment.

### Removed
- Removed TapirLink functionality (deleted `EncounterSetTapirLinkExposure` servlet, cleaned up related code, UI, and resource bundle entries).
- Removed GBIF exposure UI (deleted `MassExposeGBIF` servlet, removed servlet mappings, and stripped admin UI form).
