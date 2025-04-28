# Wildbook PostGIS Functionality


## General notes

To use PostGIS, you must have PostGIS installed on your system, which can be installed via package manager
such as `apt-get install postgresql-9.5-postgis-2.2`.  Additionally, it must be enabled on a per-database basis
by executing the following psql command (in the database) _as the user **postgres**_: `CREATE EXTENSION postgis;`


## Installing

* `zcat simplified_water_polygons.sql.gz | psql`

* `psql < water_functions.sql`


## Functions

### water_functions.sql

These are intended to be used with the *simplified_water_polygons* dataset below.  They provide the following functions in psql:

* `toMercatorGeometry(decimalLatitude, decimalLongitude)` - given (lat,lon) returns a PostGIS Point object in Mercator format

* `validLatLon(decimalLatitude, decimalLongitude)` - returns _boolean_ true if (lat,lon) are in valid range

* `overlappingWaterGeometries(decimalLatitude, decimalLongitude, radius)` - returns matching rows of PostGIS Geometries (polygons)
  if the (lat,lon) point is within _radius_ of any water polygon geometries.  The radius value _seems_ to be in meters, but has
  not been verified.  A value of around 200-500 seems to be sufficient for "close" (i.e. visual range). YMMV.

* `nearWater(decimalLatitude, decimalLongitude, radius)` - returns _boolean_ true if (lat,lon) are is near any ocean water;
  that is, it has one or more overlapping Geometries

These are exposed in java via the **Util.java** class as `overlappingWaterGeometries()` and `nearWater()`.


## Datasets

### simplified_water_polygons

Derived from [Open Street Maps _Water polygons_](https://osmdata.openstreetmap.de/data/water-polygons.html), specifically
the **Simplified polygons** (Mercator).  This data is
[under the Open Database License](https://osmdata.openstreetmap.de/info/license.html).

If you wish to use an updated version of the source data, you can perform the following steps:

* Download the data zip file from the above link, and unzip it

* It should contain a ShapeFile (`.shp`) such as **simplified_water_polygons.shp** which can be converted to
  SQL with this command: `shp2pgsql simplified_water_polygons.shp simplified_water_polygons > data.sql`

* Drop the table (`DROP TABLE simplified_water_polygons`) first, assuming it already exists; then create it again
  with the new data by using `data.sql`

* You must then set the data to _Mercator_ with this psql command:
  `SELECT UpdateGeometrySRID('simplified_water_polygons', 'geom', 3857);`

