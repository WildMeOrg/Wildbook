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

## EDM Start

Now the EDM can be started, so run tomcat with the appropriate database configuration.  You can confirm the correct EDM is running at the url
`/edm/json/git-info.json` at the host/port for your EDM.

## Migrating to CustomFields

The three major new-world EDM classes (Encounters, Sightings, Individuals) only carry a basic set of properties.  Codex allows for additional
properties via _CustomFields_ on the EDM objects.  To decide what these will be requires knowledge of what is needed to carry over from
old world to new.   In this example, we will use a
[property](https://github.com/WildMeOrg/Wildbook/blob/master/src/main/java/org/ecocean/Occurrence.java#L51)
on EDM **Occurrence** (new world: _Sighting_) called **individualCount** (an int value).

Go to `/edm/migrate.jsp` and choose the appropriate _class_ and _field_, in this case **Occurrence** and type `individualCount`.
Hit _Validate_ button.  You should see output with the suggested contents for the new CustomField.  Verify that its properties are correct,
especially type ("integer" in this case).

