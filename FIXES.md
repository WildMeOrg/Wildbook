# Wildbook Code Cleanup Plan

This document outlines the plan to address outstanding technical debt by removing obsolete code, specifically related to GitHub issues #356 and #401.

## 1. Issue #401: Remove TapirLink Functionality

**Goal:** Completely remove all code, configuration, and UI elements related to the obsolete "TapirLink" feature.

### Analysis

A codebase search for "TapirLink" identified the following files for modification or deletion:

- **Java Servlets:**
  - `src/main/java/org/ecocean/servlet/EncounterSetTapirLinkExposure.java`
- **Java Source Files (Logic):**
  - `src/main/java/org/ecocean/Encounter.java`
  - `src/main/java/org/ecocean/CommonConfiguration.java`
- **Configuration Files:**
  - `src/main/webapp/WEB-INF/web.xml`
  - `src/main/resources/bundles/commonConfiguration.properties`
  - `src/main/resources/bundles/de/header.properties`
  - `src/main/resources/bundles/en/header.properties`
  - `src/main/resources/bundles/es/header.properties`
  - `src/main/resources/bundles/fr/header.properties`
  - `src/main/resources/bundles/it/header.properties`
  - `src/main/resources/org/ecocean/batch/BatchParser.properties`
- **JSP Files (UI):**
  - `src/main/webapp/encounters/encounter.jsp`

### Step-by-Step Removal Plan

1.  **Delete Core Servlet:**
    -   Delete the file: `src/main/java/org/ecocean/servlet/EncounterSetTapirLinkExposure.java`.

2.  **Remove Servlet Definition from `web.xml`:**
    -   Find and remove the `<servlet>` and `<servlet-mapping>` blocks corresponding to `EncounterSetTapirLinkExposure` from `src/main/webapp/WEB-INF/web.xml`.

3.  **Remove Configuration Properties:**
    -   Search within all identified `.properties` files and remove any key-value pairs related to `TapirLink`.

4.  **Remove UI Elements:**
    -   Edit `src/main/webapp/encounters/encounter.jsp` to remove any buttons, links, or scripts that call or display TapirLink functionality.

5.  **Clean Up Java Code:**
    -   Edit `Encounter.java` and `CommonConfiguration.java` to remove any methods, variables, or logic that reference `TapirLink` or the `EncounterSetTapirLinkExposure` servlet.

## 2. Issue #356: Remove GBIF Exposure UI

**Goal:** Remove the obsolete user interface for GBIF exposure.

*This issue will be addressed after the TapirLink removal is complete.*

### Step-by-Step Removal Plan

1.  **Delete Core Servlet:** Delete the file `src/main/java/org/ecocean/servlet/MassExposeGBIF.java`.
2.  **Remove Servlet Definition:** Remove the `<servlet>` and `<servlet-mapping>` entries for `MassExposeGBIF` from `src/main/webapp/WEB-INF/web.xml`, including the security constraint `/MassExposeGBIF`.
3.  **Remove UI Elements:** Edit `src/main/webapp/appadmin/admin.jsp` to remove the GBIF exposure form.
4.  **Clean Up Code:** Verify no remaining references to `MassExposeGBIF` exist in the codebase.
5.  **Test and Build:** Run `mvn clean install` to ensure stability.

This removal will be followed by testing the application to confirm functionality remains unaffected.

This plan will be followed to ensure a clean and safe removal of the obsolete code. Each major step will be followed by a test build to ensure the application remains stable.

---

## Final Note

This cleanup plan is based on three key sources of understanding:

1. **Project Documentation:** GitHub issues #401 and #356 (and the draft PR #770) explicitly state that the TapirLink and GBIF Exposure UI features are obsolete and ready for removal.
2. **Code Analysis:** A detailed grep search identified the exact locations and self-contained nature of the code implementing these features, confirming they can be safely removed.
3. **External Context:** TapirLink is a defunct biodiversity data protocol superseded by modern standards (e.g., Darwin Core), and GBIF exposure is now automated, rendering the old UI unnecessary.

By combining these sources, we ensure the removals eliminate truly obsolete code without impacting current functionality.
