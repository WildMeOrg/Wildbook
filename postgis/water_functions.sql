DROP FUNCTION IF EXISTS overlappingWaterGeometries(FLOAT, FLOAT, FLOAT);
DROP FUNCTION IF EXISTS validLatLon(FLOAT, FLOAT);
DROP FUNCTION IF EXISTS nearWater(FLOAT, FLOAT, FLOAT);
DROP FUNCTION IF EXISTS toMercatorGeometry(FLOAT, FLOAT);

-- input order: (lat, lon) !  (this is mostly utilitarian / internal-use)
CREATE FUNCTION toMercatorGeometry(FLOAT, FLOAT) RETURNS geometry AS
'
SELECT
	ST_Transform(
		ST_SetSRID(
			ST_Point($2, $1),
			4326
		),
		3857
	)
;
'
language 'sql';

CREATE FUNCTION validLatLon(FLOAT, FLOAT) RETURNS BOOLEAN AS
'
	SELECT NOT (($1 < -90) OR ($1 > 90) OR ($2 < -180) OR ($2 > 180));
'
language 'sql';


-- please note:  (lat, lon, radiusInMeters)
CREATE FUNCTION overlappingWaterGeometries(FLOAT, FLOAT, FLOAT) RETURNS SETOF simplified_water_polygons AS
$$
	SELECT * FROM 
		simplified_water_polygons
		WHERE validLatLon($1, $2) AND ST_DWithin(
			geom,
			toMercatorGeometry($1, $2),
			$3
		)
	;
$$
language 'sql';


CREATE FUNCTION nearWater(FLOAT, FLOAT, FLOAT) RETURNS BOOLEAN AS
'
	SELECT (COUNT(*) > 0) FROM overlappingWaterGeometries($1, $2, $3);
'
language 'sql';


