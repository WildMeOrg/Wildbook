# Overview

## Scripts

There are three _scripts_ here.  This is what they are for:

* ~~**prop2json.pl**~~ - this should _never be used_!  It is(was) a tool for the _original_ creation of the .json files in this directory.
* **json2trans.pl** - this generates a json hash for all the i18n text keys based on the .json definition files, so as to allow assigning human-readable values in any language.  It can be run (from this directory) e.g `./json2trans.pl > config_lang.json`.
* **prop2sql.pl** - this is the _main tool_ for converting from **existing .properties** files into **sql** suitable for migrating to new Configuration support.  It can be run such as `./prop2sql {PATH_CONFIG-JSON_DIR} {PATH_TO_CONFIG_BUNDLES} {PATH_TO_CONFIG_BUNDLES_OVERRIDE} > conf.sql`; for example: `./prop2sql  /var/lib/tomcat8/webapps/wildbook_data_dir/WEB-INF/classes/bundles/config-json/  /var/lib/tomcat8/webapps/wildbook/WEB-INF/classes/bundles/  /var/lib/tomcat8/webapps/wildbook_data_dir/WEB-INF/classes/bundles/ > conf.sql`.  **Note:** comments (in sql output) will be given for .properties files/values which appear to not have valid definition counterparts; so these should be noted, such that the _definitions can be updated_ accordingly and the script _run again_.

## JSON Files

The **.json** files are **"definitions"** of the _structure_ and _specific details_ around the valid configuration options.
They represent the "seeds" from which the _generated_ (defining, for UI) JSON for the front-end is produced.  It also contains
default values (when applicable) and validation parameters.

Each object will have a unique **ID** which is programatically constructed from the JSON "path" with an acceptable
delimiter (TBD, using `_` for now), which will always have the prefix `configuration`.
So, such a might be `configuration_socialAuth_social_flickr_auth_key` (coming from [socialAuth.json](socialAuth.json)). Notably, these IDs _are not_ in the
files themselves, as they are generatable and would thus be redundant.

### The json definition content then serve three purposes:

1. Provided valid class & parameters for verifying the input from the user

2. When java code asks for `getProperty(id)`, a default value can (optionally) be provided if the site has not been configured otherwise.

3. Provide guidance for the site configuration UI on how to construct the form for asking the admin for values to persist in the db.

# JSON Files: Content description 

The JSON content describes a "tree" of settings beneath the top-level (which is represented by the file itself, e.g. `socialAuth.json` = _socialAuth_).  The path to the _leaf nodes_ represent sub-sections of configuration.  For example, `socialAuth.json` might contain `{ google: { apiKey: {...} }`, which means there is a sub-section for "google" which has a leaf (setting) for "apiKey".  This _path_ is represented by the key **socialAuth.google.apiKey** when accessing via java Configuration calls.  Further, it would generate an ID (for the front end) of `socialAuth_google_apiKey` and corresponding _set_ of translation keys based off that, such as `SOCIALAUTH_GOOGLE_APIKEY_LABEL`, `SOCIALAUTH_GOOGLE_APIKEY_DESCRIPTION`, `SOCIALAUTH_GOOGLE_APIKEY_HELPTEXT` and so on.

## Setting definitions

"Leaf" nodes as described above, should have a special structure to describe how values can be set.  This has the key **__meta**, and would reside (in our above example) such as: `{ google: apiKey: { __meta: {}, .... } }`.  The contents of this `__meta` data is:

* **type** - (required) contains type of data that will be stored.  Varied values range from "special" to full java classes.  #TODO

* **defaultValue** - (optional) contains the default value for this property.

* **required** - (_boolean_, optional) whether there must be a value here

* **values** - (optional) some TBD way of describing what valid values are

* **multipleMin**, **multipleMax** - (optional) implies (potentially) multiple choices possible, bounded by values (unset max with min set means no limit); if _multipleMin_ > 0 this implies **required**.

* **formSchema** - (optional) contains "hints" for generating the json the ui will use to construct the form. TBD

* _see also **branching** info below_

```json
{
    "defaultValue": 100,
    "type": "integer",
    "required": true,
    "formSchema": {
        "css-class": "special"
    }
}
```
This might _generate output_ something along the lines of this, for usage by the **front-end UI**:

```json
{
    "id": "configuration_path_foo_bar",
    "translationId": "CONFIGURATION_PATH_FOO_BAR",
    "required": true,
    "defaultValue": 100,
    "fieldType": "integer",
    "css-class": "special"
}
```

Current development / work-in-progress, subject to change.  Based on [this example](https://github.com/WildbookOrg/wildbook-frontend/blob/master/src/constants/userSchema.js).

### Misc. options and caveats

* **formSchema.valueText** - (_boolean_, optional) is a hash for _labels_ (viewed by user) associated with the options provided in a (required if this is present) **values** list.  e.g `values: ["a", "b"], formSchema: { valueText: { a: "Apple", b: "Banana" } }`.  Note: this does _not_ implicitely support i18n; see below for variation to help with this.

* **_alternate_ i18n** - better would be for the frontend to construct consistent/predictable _translationIds_ based on the setting ID combined
with the option value, such as: `configuration_path_foo_bar_option_a` (as the label for value `a`).

### Branching (WIP)

Nodes in the configuration tree may be optional _dependent on other settings_ (i.e. user choices).  Some potential properties to control this might be:

* `.enableIf`, `.disableIf` - references an ID, which _must_ contain a boolean value.  This node (and sub-tree) will be enabled/disabled based on parity
of the configuration at that ID.  (Otherwise it will be the opposite; disabled/enabled.)  Potentially can establish consistent message IDs for these cases, such as `<TRANSLATION-ID-PREFIX>_DISABLED_MESSAGE` etc.


# Potential development?

Values (within the .json) might benefit from some sort of lookup method (e.g. into Configuration java class) based on the **ID**.
This would allow a programmatic way to, say, populate the value based on java code.
This might look like: `"foo": { "lookup": true }` which would translate to calling something like `Configuration.jsonValueLookup("configuration_path_here_foo")` --
which would return an Object to set on that value (e.g. String, JSONObject, JSONArray, etc).

