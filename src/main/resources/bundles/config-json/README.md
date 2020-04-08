
Each object will have a unique **Key** which is programatically constructed from the JSON "path" with an acceptable
delimiter (TBD, using `_` for now).  So, such a might be `socialAuth_social_flickr_auth_key` (coming from **socialAuth.json**).

**`.formSchema`** should be used as _hints_ for constructing the appropriate _actual_ json schema suitable for the
chosen form-generating library, such as this one below from [jsonforms.io](https://jsonforms.io/examples/categorization).
For example, `.formSchema` might contain only the _non-generatable_ properties, such as `{"type": "string", "required": true}`; and the rest
would be created algorithmically (the Key, description field, etc).
In the generated form schema _literal text_ (such as `.description`)
will instead be _localized_ based on the **Key** and the field, e.g. `socialAuth_social_flickr_auth_key_description`.


```json
{
  "type": "object",
  "properties": {
    "name": {
      "type": "string",
      "minLength": 3,
      "description": "Please enter your name"
    },
    "vegetarian": {
      "type": "boolean"
    },
    "birthDate": {
      "type": "string",
      "format": "date",
      "description": "Please enter your birth date."
    },
    "nationality": {
      "type": "string",
      "enum": [
        "DE",
        "IT",
        "JP",
        "US",
        "RU",
        "Other"
      ]
    }
  }
}
```

Values might benefit from some sort of lookup method (e.g. into Configuration java class) based on the **Key**.  This would allow a
programmatic way to, say, populate the `enum` list below based on a call in java.
This might look like: `"enum": { "lookup": true }` which would translate to calling something like `Configuration.jsonValueLookup("key_foo_bar_nationality")` --
which would return an Object to set on that value (e.g. String, JSONObject, JSONArray, etc).

==UPDATE==
Currently, looks like form schema end results will look something like this:
```json
 {
    name: 'affiliation',
    translationId: 'AFFILIATION',
    defaultValue: '',
    type: 'string',
  }
```
(From ben, via [this example](https://github.com/WildbookOrg/wildbook-frontend/blob/master/src/constants/userSchema.js).)
