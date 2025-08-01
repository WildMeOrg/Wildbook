# Wildbook Project Analysis

## Overview
Wildbook is a multi-component web application for wildlife encounter management and identification. It comprises:

- **Backend:** Java Servlet/JSP application (src/main/java, src/main/webapp) built with Maven (`pom.xml`).
- **Frontend:** JavaScript/React UI under `frontend/` managed via npm/Yarn (`package.json`, `babel.config.js`).
- **DevOps:** `jetty.xml` for local Jetty server; `devops/` and `config/` folders for deployment scripts.

## Build & Execution

1. **Prerequisites**: JDK 11+, Node.js 16+ and npm/Yarn.
2. **Frontend**:
   ```bash
   cd frontend
   npm install
   npm run build   # or yarn build
   cd ..
   ```
3. **Backend**:
   ```bash
   mvn clean package   # builds WAR and runs frontend-maven-plugin
   mvn jetty:run       # starts local server (configured via jetty.xml)
   ```
4. **Access**: http://localhost:8080/

## Key Characteristics

- **Modular**: clear separation of UI and server logic.
- **I18n support** via resource bundles (`bundles/`).
- **Legacy JSP** mixed with modern React app.
- **Automated linting/hooks** (ESLint, Husky).

## Areas for Improvement

- **Legacy Cleanup**: remove outdated GBIF and TapirLink code (issues #356, #401).
- **Unified Build**: integrate frontend build in Maven or provide wrapper scripts.
- **Containerization**: add Dockerfile and Docker Compose for local dev.
- **Testing**: implement unit/integration tests for both backend (JUnit) and frontend (Jest).
- **Framework Upgrade**: consider migrating to Spring Boot for easier configuration and embedded server.
- **CI/CD**: add GitHub Actions for automated build, test, and deployment.
- **Documentation**: improve README with setup, architecture diagrams, and API docs.

*Generated on 2025-07-30.*

## Project Enhancements

This section details the documentation and scripts added to the project to explain and streamline the improvement process.

### Documentation
- **`ANALYSIS.md`**: This file, providing an overview of the project structure, build process, and areas for improvement.
- **`CHANGELOG.md`**: A log of all notable changes made to the project, documenting the removal of TapirLink and GBIF functionality.
- **`FIXES.md`**: A file outlining the specific steps required to address the identified issues, serving as a procedural guide for the cleanup.
- **`FIXING_PROCEDURE.md`**: A detailed record of the entire process, including commands run, issues encountered, and resolutions implemented during the development work.

### Utility Scripts
- **`run-wildbook.sh`**: A shell script created to simplify the process of running the application. It handles the Docker environment setup, ensuring all services start correctly and making the application easily accessible for development and testing.
