# Migration from old-world Wildbook to nextgen EDM


## Overview

Process involves some sql, some shell-based scripts, and some jsp/servlet activity.  Some hand-holding may be required due to
the minute differences between old-world Wildbook branches.  Sorry.

This process will migrate old-world data over to new-world EDM and houston, with any luck.

## Database prep

EDM (tomcat) should be _stopped_ so that the database is not accessed, and the database should be empty.

Start with a fresh restore from a backup made of old-world data at switchover point.  For example, assuming the new database is called `nextgen`,
something like:

```zcat wildbook-live-db-FINAL.sql.gz | psql -U wildbook -h localhost nextgen```

### Updating database

Once the old-world data is restored into the database, perform the following steps.  (`.sql` files are located under the `migration/` directory.)
Adjust actual postgresql user/hostname/db arguments accordingly for your configuration.

1. `cat all-in-one.sql | psql -U wildbook -h localhost nextgen`

## Start up EDM

Now the EDM can be started, so run tomcat with the appropriate database configuration.  You can confirm the correct EDM is running at the url
`/edm/json/git-info.json` at the host/port for your EDM.

## Migrating data (via houston tasks)

Once the EDM is up, some data can be migrated using houston tasks.  The first should be migrating users.

### Users

`# invoke app.users.sync-edm` should migrate all historical users from EDM to houston.


## Migrating data (via EDM urls)

Using the EDM, migrating data can be done from the `/edm/migrate/` url.  It will list links to the following:

### Regions

Manipulate and migrate <b>locationID.json</b> data using `/edm/migrate/locationID.jsp`.

### Taxonomies

Update `site.species` site settings as well as set proper taxonomies on Encounters using `/edm/migrate/taxonomies.jsp`.

### DateTime

The option at `/edm/migrate/datetime.jsp` will migrate date-related values to the new
[ComplexDateTime](https://github.com/WildMeOrg/Wildbook/blob/next-gen/src/main/java/org/ecocean/ComplexDateTime.java) data type.

_Note:_ It is very helpful to have your
[locationID.json](https://github.com/WildMeOrg/Wildbook/blob/next-gen/src/main/resources/bundles/locationID.json) file updated
_before_ this step to include a `timeZone` [value](https://mkyong.com/java8/java-display-all-zoneid-and-its-utc-offset/) for each locationID.

### Occurrence / Sightings

`/edm/migrate/occurs.jsp` will generate Sightings for Encounters that have none.

### Assets / Annotations / Keywords

`/edm/migrate/assets.jsp` will assist in migrating Assets, Annotations, and Keywords.

This will involve shell scripts and SQL.  Good luck!

### CustomFields

The three major new-world EDM classes (Encounters, Sightings, Individuals) only carry a basic set of properties.  Codex allows for additional
properties via _CustomFields_ on the EDM objects.  To decide what these will be requires knowledge of what is needed to carry over from
old world to new.   In this example, we will use a
[property](https://github.com/WildMeOrg/Wildbook/blob/master/src/main/java/org/ecocean/Occurrence.java#L51)
on EDM **Occurrence** (new world: _Sighting_) called `individualCount` (an int value).

The link to `/edm/migrate/customFields.jsp` and choose the appropriate _class_ and _field_, in this case **Occurrence** and type `individualCount`.
Hit _Validate_ button.  You should see output with the suggested contents for the new CustomField.  Verify that its properties are correct,
especially type ("integer" in this case).

### Behaviors

`/edm/migrate/behaviors.jsp` will migrate behavior data to CustomFields on Encounters and Occurrences.

### Measurements

Similarly, use `/edm/migrate/measurements.jsp` in order to migrate **Measurement** values on **Encounter** data to CustomFields.

### LabeledKeywords

`/edm/migrate/labeledkeywords.jsp` will migrate **LabeledKeywords** values on MediaAssets to **Encounter** data as CustomFields.

