# Search in Wildbook

## Overview

Searching on Wildbook is handled by [OpenSearch](https://opensearch.org), which runs on its own docker container. Wildbook uses the
[OpenSearch.java](src/main/java/org/ecocean/OpenSearch.java) class and methods within the [Base.java](src/main/java/org/ecocean/Base.java) class (and subclasses) to
handle the indexing.

Searching on Wildbook indices is done through the [Search API servlet](src/main/java/org/ecocean/api/SearchApi.java) by POSTing OpenSearch Query JSON.

## Indexing details

Indexing of classes (currently only Encounters) are done in a variety of ways, with the goal of index syncing being fully automatic.

1. Manual index creation / syncing - primarily intended for first pass at indexing on legacy data
2. Indexing as object persistence happens, via hooks on the DataNucleus internals using [LifecycleListener](src/main/java/org/ecocean/WildbookLifecycleListener.java), as well as
hooks within deletion methods to un-index removed objects
3. A background indexing mechanism which regularly compares indices to actual data to catch mis-indexed objects

### Caveats and considerations

- Class indexes contain data which rely on _other classes_ for information; for example, indexing an Encounter will reference that encounter's MarkedIndividual to get search fields.
This necessitates the need for _cascading index triggers_ -- in this example, when a MarkedIindividual changes, it must also trigger re-indexing of all Encounters which reference that individual. This is to be handled by the "deep" methods (e.g. `indexDeep()`) on objects. **It is currently incomplete and will develop as other classes gain support for indexing.** This means that some values will not change within indexes until the background indexing happens. (In our example, the encounter index will not reflect the Individual change until the background index catches up.)

- Background indexing is built to rely heavily on a `version` value within the class, in order to index only what "has changed". Most code in Wildbook is already making the change to this
field, but there are places where it is neglected. These will be fixed as discovered. Note: this _does not_ affect the persistence-triggered indexing (2 above).

## Querying the API and pagination

Getting results back from the search API will be "paged" with a subset of results when there are a large number of matches. This paging is controlled with the `from` and `size`
parameters. Notably, paging is done in OpenSearch via [PIT (point-in-time) functionality](https://opensearch.org/docs/latest/search-plugins/searching-data/point-in-time/), which is
the preferred method of paginating.

The query (and thus results) are done against a sort of "snapshot" of the index, that is frozen in time. This has numerous benefits including efficiency and preventing "drifting"
pages and results (if the index changes while the user is paging through the results). It also means the PIT snapshot is showing data as it was at the time of being frozen. This can
mean stale results, but that is really more of a benefit, as it means the results *do not change* as the user pages.

The way Wildbook handles PIT create and expiration is a little experimental right now. It can also be controlled via parameters on the query itself. The details and process should
be monitored in real-world use so default parameters can be tweaked as needed.

The _total results count_ can be found in the header on any search results as `X-Wildbook-Total-Hits: 12345`.
