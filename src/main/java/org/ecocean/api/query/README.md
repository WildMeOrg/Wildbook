# Querying the EDM

To query _ecological data_ from the **EDM**, the API is sent JSON query objects.  The syntax is based on
[MongoDB Document Queries](https://docs.mongodb.com/manual/tutorial/query-documents/).


## Example

Here is an example equivalent to the _"WHERE clause"_ part of an SQL statement, with explanations below.

```json
{
        "val": {"$in": [
                "a",
                4.1,
                5,
                6
        ]},

        "foo": null,

        "cat": "meow",

        "$or": [
                {"color": {"$lt": 99}},
                {"colour": {"$ne": 2}}
        ],

        "$and": [
                {"fu": {"$lte": 3}},
                {"fu": {"$ne": null}}
        ],

        "dog": "woof"
}
```

Special arguments begin with **$**, such as `$in`.  The blocks map to these equivalents in SQL (and would be `AND`ed together):

* ` val IN ('a', 4.1, 5, 6) `.
* `foo IS NULL`
* `cat == 'meow'`
* `(color < 99) OR (colour != 2)`
* `(fu <= 3) AND (fu IS NOT NULL)`
* `dog == 'woof'`

### A note on `$and`

Explicitly using `$and` allows for _duplicate property names_ (`fu` in the above example) that would not otherwise be possible with the default
**AND** behavior based on JSON object keys (which can only appear once in JSON).

