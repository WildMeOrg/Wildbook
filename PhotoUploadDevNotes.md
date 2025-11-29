# Photo Upload

There are currently three places to change the default photo upload size and default max count amount.

This is linked from comments in the corresponding non-development properties files which says: `Refer to PhotoUploadDevNotes.md for information/where else to change`. Properties files are not annotated with this comment since those files will be changed by users.

Order of using:
1. `commonConfiguration.properties`, which each production Wildbook can change if needed
    - ./devops/deploy/.dockerfiles/tomcat/commonConfiguration.properties
    - ./devops/development/.dockerfiles/tomcat/commonConfiguration.properties
    - ./src/main/resources/bundles/commonConfiguration.properties
2. `org/ecocean/CommonConfiguration.java`, first fallback if properties are not initialized
3. `frontend/src/constants/photoUpload.js`, second fallback if SiteSettings is not accessible