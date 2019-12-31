DROP FUNCTION IF EXISTS toMercatorGeometry(FLOAT, FLOAT);
DROP FUNCTION IF EXISTS validLatLon(FLOAT, FLOAT);

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


--  example (using postgis ST_Distance() function):
--
--  SELECT *, ST_Distance(toMercatorGeometry("DECIMALLATITUDE", "DECIMALLONGITUDE"),toMercatorGeometry(45.5201, -122.681944)) AS dist FROM "ENCOUNTER" WHERE validLatLon("DECIMALLATITUDE", "DECIMALLONGITUDE") AND ST_Distance(toMercatorGeometry("DECIMALLATITUDE", "DECIMALLONGITUDE"),toMercatorGeometry(45.5201, -122.681944)) < 4000 ORDER BY dist;



