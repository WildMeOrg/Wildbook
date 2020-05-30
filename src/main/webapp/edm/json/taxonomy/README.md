# Taxonomy JSON Files

These files are used for configuration of Wildbook.  This data is used to populate the database with proper
[Taxonomy Objects](https://github.com/WildbookOrg/Wildbook/blob/master/src/main/java/org/ecocean/Taxonomy.java)
based on these "seed" values.

## Species-specific JSON Files

A file should be present for each species.  These files should represent the total list of supported species by the Wildbook project.
When setting up individual Wildbooks, these will be presented as options to the administrator during configuration.

File names are based on [ITIS](https://itis.gov/) _Taxonomic Serial Number (TSN)_.  For example, the TSN for _Rhinocondon typus_
(whale shark) is
[159857](https://itis.gov/servlet/SingleRpt/SingleRpt?search_topic=TSN&search_value=159857),
so the file would be **159857.json**.  Example content is given below, which includes scientific name and localized common names keyed
by language code.  (Note that the prefered localized common name should be the first entry in the array, when more than one is given.)

```json
{
	"scientificName": "Rhincodon typus",
	"commonNames": {
		"en": [ "whale shark" ],
		"es": [ "tiburón ballena", "dámero" ],
		"jp": [ "ジンベイザメ" ]
	}
}
```

Species JSON files will appear in the configuration if they are listed in the `single` array of **select.json** (described below).

## Bundles and select.json

For convenience purposes, species can be combined into "bundles" as a way to present a single choice to add multiple species.
These are defined in **select.json**, which also includes the list of _individual_ species to show in configuration, as an array under
the key `single`.  An example select.json is shown here:

```json
{
	"single": [
		159857,
		180488,
		180426,
		180530
	],

	"bundles": {
		"cetaceans": [ 180488, 180530, "bundle_dolphins" ],
		"dolphins": [ 180426 ]
	}
}
```

Bundles must have a _unique key_ (composed of letters/underscore), and include a list of TSN values with valid JSON files.
The list may also include references to other bundles by using the `bundle_KEY` notation, as in "*bundle_dolphins*" above.
This is shorthand for including the species from one bundle within another.

## Internationalization

For the purposes of configuration, _translation keys_ for the bundles will be of the form *CONFIGURATION_TAXONOMY_SELECT_BUNDLE_bundleKey*
(such as `CONFIGURATION_TAXONOMY_SELECT_BUNDLE_CETACEANS`) and as such should be added to **lang/configuration.XX.json** accordingly.

> ⚠️  Details for **localization of common names** is currently TBD!  Proposed is using a translation key of the form
> *TAXONOMY_COMMON_NAME_tsn* (e.g. `TAXONOMY_COMMON_NAME_159857`).  This would translate to the initial element in the `commonNames` array
> defined in the species JSON file.
>
> It is uncertain how these phrases would even be used, and may require other ideas, such as a translation key which will be substituted
> with a localized _list_ of the species supported by the Wildbook instance.


