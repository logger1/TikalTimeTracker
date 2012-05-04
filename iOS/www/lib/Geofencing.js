/**
 * Geofencing.js
 *  
 * Phonegap Geofencing plugin
 * Copyright (c) Dov Goldber 2012
 *
 */
var Geofencing = {
	/*
	Params:
	#define KEY_REGION_ID       @"fid"
	#define KEY_PROJECT_NAME    @"projectname"
	#define KEY_PROJECT_LAT     @"latitude"
	#define KEY_PROJECT_LNG     @"longitude"
	*/
     addRegion: function(params, success, fail) {
          return PhoneGap.exec(success, fail, "Geofencing", "addRegion", [params]);
     }
};