var GeoJSON = function( geojson, options, map, bounds,aspect ){

	var _geometryToGoogleMaps = function( geojsonGeometry, opts, geojsonProperties,aspect ){
		
		var googleObj;
		
		
		switch ( geojsonGeometry.type ){
			case "Point":
				opts.position = new google.maps.LatLng(geojsonGeometry.coordinates[1], geojsonGeometry.coordinates[0]);
				googleObj = new google.maps.Marker(opts);
				
				if(aspect == "sex"){
					googleObj.setIcon('https://chart.googleapis.com/chart?chst=d_map_pin_letter&chld=|'+geojsonGeometry.sexColor);
				}
				else if(aspect == "haplotype"){
					googleObj.setIcon('https://chart.googleapis.com/chart?chst=d_map_pin_letter&chld=|'+geojsonGeometry.haplotypeColor);
				}
				else if(aspect == "species"){
									googleObj.setIcon('https://chart.googleapis.com/chart?chst=d_map_pin_letter&chld=|'+geojsonGeometry.speciesColor);
				}
				else{
					googleObj.setIcon('https://chart.googleapis.com/chart?chst=d_map_pin_letter&chld=|'+geojsonGeometry.color);
				}
				
				
					 
				//reset bounds
				bounds.extend(googleObj.getPosition());
				map.fitBounds(bounds);
				google.maps.event.addListener(googleObj,'click', function() {
					(new google.maps.InfoWindow({content: '<strong><a target=\"_blank\" href=\"http://'+geojsonGeometry.rootURL+'/individuals.jsp?number='+geojsonGeometry.individualID+'\">'+geojsonGeometry.individualID+'</a></strong><br /><table><tr><td><img align=\"top\" border=\"1\" src=\"/'+geojsonGeometry.dataDirectoryName+'/encounters/'+geojsonGeometry.encSubdir+'/thumb.jpg\"></td><td>Date: '+geojsonGeometry.date+'<br /><br /><a target=\"_blank\" href=\"http://'+geojsonGeometry.rootURL+'/encounters/encounter.jsp?number='+geojsonGeometry.catalogNumber+'\" >Go to encounter</a></td></tr></table>'})).open(map, this);		
					
				});


				if (geojsonProperties) {
					googleObj.set("geojsonProperties", geojsonProperties);
				}
				break;
				
			case "MultiPoint":
				googleObj = [];
				for (var i = 0; i < geojsonGeometry.coordinates.length; i++){
					opts.position = new google.maps.LatLng(geojsonGeometry.coordinates[i][1], geojsonGeometry.coordinates[i][0]);
					googleObj.push(new google.maps.Marker(opts));
				}
				if (geojsonProperties) {
					for (var k = 0; k < googleObj.length; k++){
						googleObj[k].set("geojsonProperties", geojsonProperties);
					}
				}
				break;
				
			case "LineString":
				var path = [];
				for (var i = 0; i < geojsonGeometry.coordinates.length; i++){
					var coord = geojsonGeometry.coordinates[i];
					var ll = new google.maps.LatLng(coord[1], coord[0]);
					path.push(ll);
				}
				opts.path = path;
				
				//determine stroke color
				var polyLineStrokeColor;
				if(aspect == "sex"){
					polyLineStrokeColor=geojsonGeometry.sexColor;
				}
				else if(aspect == "haplotype"){
					polyLineStrokeColor=geojsonGeometry.haplotypeColor;
				}
				else if(aspect == "species"){
					polyLineStrokeColor=geojsonGeometry.speciesColor;
				}
				else{
					polyLineStrokeColor=geojsonGeometry.color;
				}
				
				googleObj = new google.maps.Polyline({
	 				       path: path,
	 				       visible: true,
	 				       geodesic: true,
	 				       strokeOpacity: 0.0,
	 				       strokeColor: ('#'+polyLineStrokeColor),
	 				       icons: [{
	 				         icon: {
	 				           path: 'M -1,1 0,0 1,1',
	 				           strokeOpacity: 1,
	 				           strokeWeight: 1.5,
	 				           scale: 6,
	 				          
	 				         },
	 				         repeat: '20px'
	 				         
	 				       }
	 				       ],
	 				       map: map
	     			});
	     			
				if (geojsonProperties) {
					googleObj.set("geojsonProperties", geojsonProperties);
				}
				break;
				
			case "MultiLineString":
				googleObj = [];
				for (var i = 0; i < geojsonGeometry.coordinates.length; i++){
					var path = [];
					for (var j = 0; j < geojsonGeometry.coordinates[i].length; j++){
						var coord = geojsonGeometry.coordinates[i][j];
						var ll = new google.maps.LatLng(coord[1], coord[0]);
						path.push(ll);
					}
					opts.path = path;
					googleObj.push(new google.maps.Polyline(opts));
				}
				if (geojsonProperties) {
					for (var k = 0; k < googleObj.length; k++){
						googleObj[k].set("geojsonProperties", geojsonProperties);
					}
				}
				break;
				
			case "Polygon":
				var paths = [];
				var exteriorDirection;
				var interiorDirection;
				for (var i = 0; i < geojsonGeometry.coordinates.length; i++){
					var path = [];
					for (var j = 0; j < geojsonGeometry.coordinates[i].length; j++){
						var ll = new google.maps.LatLng(geojsonGeometry.coordinates[i][j][1], geojsonGeometry.coordinates[i][j][0]);
						path.push(ll);
					}
					if(!i){
						exteriorDirection = _ccw(path);
						paths.push(path);
					}else if(i == 1){
						interiorDirection = _ccw(path);
						if(exteriorDirection == interiorDirection){
							paths.push(path.reverse());
						}else{
							paths.push(path);
						}
					}else{
						if(exteriorDirection == interiorDirection){
							paths.push(path.reverse());
						}else{
							paths.push(path);
						}
					}
				}
				opts.paths = paths;
				googleObj = new google.maps.Polygon(opts);
				if (geojsonProperties) {
					googleObj.set("geojsonProperties", geojsonProperties);
				}
				break;
				
			case "MultiPolygon":
				googleObj = [];
				for (var i = 0; i < geojsonGeometry.coordinates.length; i++){
					var paths = [];
					var exteriorDirection;
					var interiorDirection;
					for (var j = 0; j < geojsonGeometry.coordinates[i].length; j++){
						var path = [];
						for (var k = 0; k < geojsonGeometry.coordinates[i][j].length; k++){
							var ll = new google.maps.LatLng(geojsonGeometry.coordinates[i][j][k][1], geojsonGeometry.coordinates[i][j][k][0]);
							path.push(ll);
						}
						if(!j){
							exteriorDirection = _ccw(path);
							paths.push(path);
						}else if(j == 1){
							interiorDirection = _ccw(path);
							if(exteriorDirection == interiorDirection){
								paths.push(path.reverse());
							}else{
								paths.push(path);
							}
						}else{
							if(exteriorDirection == interiorDirection){
								paths.push(path.reverse());
							}else{
								paths.push(path);
							}
						}
					}
					opts.paths = paths;
					googleObj.push(new google.maps.Polygon(opts));
				}
				if (geojsonProperties) {
					for (var k = 0; k < googleObj.length; k++){
						googleObj[k].set("geojsonProperties", geojsonProperties);
					}
				}
				break;
				
			case "GeometryCollection":
				googleObj = [];
				if (!geojsonGeometry.geometries){
					googleObj = _error("Invalid GeoJSON object: GeometryCollection object missing \"geometries\" member.");
				}else{
					for (var i = 0; i < geojsonGeometry.geometries.length; i++){
						googleObj.push(_geometryToGoogleMaps(geojsonGeometry.geometries[i], opts, geojsonProperties || null));
					}
				}
				break;
				
			default:
				googleObj = _error("Invalid GeoJSON object: Geometry object must be one of \"Point\", \"LineString\", \"Polygon\" or \"MultiPolygon\".");
		}
		
		return googleObj;
		
	};
	
	var _error = function( message ){
	
		return {
			type: "Error",
			message: message
		};
	
	};

	var _ccw = function( path ){
		var isCCW;
		var a = 0;
		for (var i = 0; i < path.length-2; i++){
			a += ((path[i+1].lat() - path[i].lat()) * (path[i+2].lng() - path[i].lng()) - (path[i+2].lat() - path[i].lat()) * (path[i+1].lng() - path[i].lng()));
		}
		if(a > 0){
			isCCW = true;
		}
		else{
			isCCW = false;
		}
		return isCCW;
	};
		
	var obj;
	
	var opts = options || {};
	
	switch ( geojson.type ){
	
		case "FeatureCollection":
			if (!geojson.features){
				obj = _error("Invalid GeoJSON object: FeatureCollection object missing \"features\" member.");
			}else{
				obj = [];
				for (var i = 0; i < geojson.features.length; i++){
					obj.push(_geometryToGoogleMaps(geojson.features[i].geometry, opts, geojson.features[i].properties,aspect));
				}
			}
			break;
		
		case "GeometryCollection":
			if (!geojson.geometries){
				obj = _error("Invalid GeoJSON object: GeometryCollection object missing \"geometries\" member.");
			}else{
				obj = [];
				for (var i = 0; i < geojson.geometries.length; i++){
					obj.push(_geometryToGoogleMaps(geojson.geometries[i], opts,aspect));
				}
			}
			break;
		
		case "Feature":
			if (!( geojson.properties && geojson.geometry )){
				obj = _error("Invalid GeoJSON object: Feature object missing \"properties\" or \"geometry\" member.");
			}else{
				obj = _geometryToGoogleMaps(geojson.geometry, opts, geojson.properties,aspect);
			}
			break;
		
		case "Point": case "MultiPoint": case "LineString": case "MultiLineString": case "Polygon": case "MultiPolygon":
			obj = geojson.coordinates
				? obj = _geometryToGoogleMaps(geojson, opts, aspect)
				: _error("Invalid GeoJSON object: Geometry object missing \"coordinates\" member.");
			break;
		
		default:
			obj = _error("Invalid GeoJSON object: GeoJSON object must be one of \"Point\", \"LineString\", \"Polygon\", \"MultiPolygon\", \"Feature\", \"FeatureCollection\" or \"GeometryCollection\".");
	
	}
	
	return obj;
	
};
