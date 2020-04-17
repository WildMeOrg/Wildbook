### Configuration

For details on how site-wide configuration is _defined_, check out [config-json/README.md](../../../resources/bundles/config-json/README.md).

### Pre-(web-based) Configuration

_Prior_ to being able to use the web-based tools that **Configuration.java** supports, some things need to be set up automatic or by
some other meta-congnizant superpower.  Some of these may be done post-tomcat launch, while others may need to be set up earlier
(such as db credentials via environment variables).

Here are some examples, and how they might be done:

* **DB credentials** - via docker or other initial setup (??) see e.g. [ShepherdPMF.java](../ShepherdPMF.java)
* **URL for server** - maybe via `CommonConfiguration.ensureServerInfo()` in a way similar to legacy _appadmin/setup.jsp_
* **Site GUID** - via `CommonConfiguration.getGUID()`, ditto above
* probably other things currently in _appadmin/setup.jsp_ (lower priority)
* **Taxonomy** - seed this, possibly via **Phone Home** magick???
* **"IA Algorithms** - see above
*  ?????
