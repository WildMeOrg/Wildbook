# Overview

These **.json** files represent the "seeds" from which the _generated_ JSON for the front-end is produced.  It also contains
default values (when applicable).

Each object will have a unique **ID** which is programatically constructed from the JSON "path" with an acceptable
delimiter (TBD, using `_` for now), which will always have the prefix `configuration`.
So, such a might be `configuration_socialAuth_social_flickr_auth_key` (coming from **socialAuth.json**).

### The json contents serve a dual purpose then:

1. when java code asks for `getProperty(id)`, a default value can (optionally) be provided if the site has not been configured (persisted in db)

2. provided guidance for the site configuration UI on how to construct the form for asking the admin for values to persist in the db

# Contents description 


**defaultValue** (optional) contains the default value for this property.

**formSchema** (optoinal) contains "hints" for generating the json the ui will use to construct the form.  Some values in the _generated_ json
(such as `name` and `translationId`) should not be supplied as they are programatically created.  Options of interest are included in this example:

```json
{
    "defaultValue": 100,
    "formSchema": {
        "type": "integer",
        "required": true
    }
}
```
This might generate output something along the lines of this, for usage by the front-end ui:

```json
{
    "name": "configuration_path_foo_bar",
    "translationId": "CONFIGURATION_PATH_FOO_BAR",
    "required": true,
    "defaultValue": 100,
    "type": "integer"
}
```

Current development, subject to change.  Based on [this example](https://github.com/WildbookOrg/wildbook-frontend/blob/master/src/constants/userSchema.js).

# Potential development?

Values (within the .json) might benefit from some sort of lookup method (e.g. into Configuration java class) based on the **ID**.
This would allow a programmatic way to, say, populate the value based on java code.
This might look like: `"foo": { "lookup": true }` which would translate to calling something like `Configuration.jsonValueLookup("configuration_path_here_foo")` --
which would return an Object to set on that value (e.g. String, JSONObject, JSONArray, etc).

