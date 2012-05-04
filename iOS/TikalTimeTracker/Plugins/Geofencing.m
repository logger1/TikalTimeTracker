//
//  Geofencing.m
//  TikalTimeTracker
//
//  Created by Dov Goldberg on 5/3/12.
//  Copyright (c) 2012 __MyCompanyName__. All rights reserved.
//

#import "Geofencing.h"

#define KEY_REGION_ID       @"fid"
#define KEY_PROJECT_NAME    @"projectname"
#define KEY_PROJECT_LAT     @"latitude"
#define KEY_PROJECT_LNG     @"longitude"

@implementation DGLocationData

@synthesize locationStatus, locationInfo, locationCallbacks;

-(DGLocationData*) init
{
    self = (DGLocationData*)[super init];
    if (self) 
	{
        self.locationInfo = nil;
        self.locationCallbacks = nil;
    }
    return self;
}
-(void) dealloc 
{
    self.locationInfo = nil;
    self.locationCallbacks = nil;
    [super dealloc];  
}

@end

@implementation Geofencing

@synthesize locationManager, locationData;

- (CDVPlugin*) initWithWebView:(UIWebView*)theWebView
{
    self = (Geofencing*)[super initWithWebView:(UIWebView*)theWebView];
    if (self) 
	{
        self.locationManager = [[[CLLocationManager alloc] init] autorelease];
        self.locationManager.delegate = self; // Tells the location manager to send updates to this object
        self.locationData = nil;
    }
    return self;
}

- (void) dealloc 
{
	self.locationManager.delegate = nil;
	self.locationManager = nil;
	[super dealloc];
}

- (BOOL) isAuthorized
{
	BOOL authorizationStatusClassPropertyAvailable = [CLLocationManager respondsToSelector:@selector(authorizationStatus)]; // iOS 4.2+
    if (authorizationStatusClassPropertyAvailable)
    {
        NSUInteger authStatus = [CLLocationManager authorizationStatus];
        return  (authStatus == kCLAuthorizationStatusAuthorized) || (authStatus == kCLAuthorizationStatusNotDetermined);
    }
    
    // by default, assume YES (for iOS < 4.2)
    return YES;
}

- (BOOL) isLocationServicesEnabled
{
	BOOL locationServicesEnabledInstancePropertyAvailable = [self.locationManager respondsToSelector:@selector(locationServicesEnabled)]; // iOS 3.x
	BOOL locationServicesEnabledClassPropertyAvailable = [CLLocationManager respondsToSelector:@selector(locationServicesEnabled)]; // iOS 4.x
    
	if (locationServicesEnabledClassPropertyAvailable) 
	{ // iOS 4.x
		return [CLLocationManager locationServicesEnabled];
	} 
	else if (locationServicesEnabledInstancePropertyAvailable) 
	{ // iOS 2.x, iOS 3.x
		return [(id)self.locationManager locationServicesEnabled];
	} 
	else 
	{
		return NO;
	}
}

#pragma mark Plugin Functions
- (void)addRegion:(NSMutableArray*)arguments withDict:(NSMutableDictionary*)options {
    
    NSUInteger argc = [arguments count];
    NSString* callbackId = (argc > 0)? [arguments objectAtIndex:0] : @"INVALID";
    
    if (![self isLocationServicesEnabled])
	{
		BOOL forcePrompt = NO;
		if (!forcePrompt)
		{
            [self returnLocationError:PERMISSIONDENIED withMessage: nil];
			return;
		}
    }
    
    if (![self isAuthorized]) 
    {
        NSString* message = nil;
        BOOL authStatusAvailable = [CLLocationManager respondsToSelector:@selector(authorizationStatus)]; // iOS 4.2+
        if (authStatusAvailable) {
            NSUInteger code = [CLLocationManager authorizationStatus];
            if (code == kCLAuthorizationStatusNotDetermined) {
                // could return POSITION_UNAVAILABLE but need to coordinate with other platforms
                message = @"User undecided on application's use of location services";
            } else if (code == kCLAuthorizationStatusRestricted) {
                message = @"application use of location services is restricted";
            }
        }
        //PERMISSIONDENIED is only PositionError that makes sense when authorization denied
        [self returnLocationError:PERMISSIONDENIED withMessage: message];
        
        return;
    }  
    
    [self saveGeofenceCallbackId:callbackId];
    
    // Parse Incoming Params
    NSString *regionId = [options objectForKey:KEY_REGION_ID];
    NSString *latitude = [options objectForKey:KEY_PROJECT_LAT];
    NSString *longitude = [options objectForKey:KEY_PROJECT_LNG];
//    if (latitude.length > 9) {
//        latitude = [latitude substringToIndex:8];
//    }
//    if (longitude.length > 9) {
//        longitude = [longitude substringToIndex:8];
//    }
    //NSString *projectName = [options objectForKey:KEY_PROJECT_NAME];
    
    CLLocationCoordinate2D coord = CLLocationCoordinate2DMake([latitude doubleValue], [longitude doubleValue]);
    CLRegion *region = [[CLRegion alloc] initCircularRegionWithCenter:coord radius:10.0 identifier:regionId];
    [self.locationManager startMonitoringForRegion:region desiredAccuracy:kCLLocationAccuracyBestForNavigation];
    [region release];
    
    [self returnRegionSuccess];
}

- (void) saveGeofenceCallbackId:(NSString *) callbackId {
    if (!self.locationData) {
        self.locationData = [[[DGLocationData alloc] init] autorelease];
    }
    
    DGLocationData* lData = self.locationData;
    if (!lData.locationCallbacks) {
        lData.locationCallbacks = [NSMutableArray array];//]WithCapacity:1];
    }
    
    // add the callbackId into the array so we can call back when get data
    [lData.locationCallbacks addObject:callbackId];
}

- (void) returnRegionSuccess; {
    NSMutableDictionary* posError = [NSMutableDictionary dictionaryWithCapacity:2];
    [posError setObject: [NSNumber numberWithInt: CDVCommandStatus_OK] forKey:@"code"];
    [posError setObject: @"Region Success" forKey: @"message"];
    CDVPluginResult* result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:posError];
    NSString *callbackId = [self.locationData.locationCallbacks pop];
    [super writeJavascript:[result toSuccessCallbackString:callbackId]];
}

- (void)returnLocationError: (NSUInteger) errorCode withMessage: (NSString*) message
{
    NSMutableDictionary* posError = [NSMutableDictionary dictionaryWithCapacity:2];
    [posError setObject: [NSNumber numberWithInt: errorCode] forKey:@"code"];
    [posError setObject: message ? message : @"" forKey: @"message"];
    CDVPluginResult* result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsDictionary:posError];
    NSString *callbackId = [self.locationData.locationCallbacks pop];
    [super writeJavascript:[result toErrorCallbackString:callbackId]];
}

#pragma mark Core Location Delegates

- (void)locationManager:(CLLocationManager *)manager monitoringDidFailForRegion:(CLRegion *)region withError:(NSError *)error {
    NSMutableDictionary* posError = [NSMutableDictionary dictionaryWithCapacity:2];
    [posError setObject: [NSNumber numberWithInt: error.code] forKey:@"code"];
    [posError setObject: region.identifier forKey: @"regionid"];
    CDVPluginResult* result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsDictionary:posError];
    NSString *callbackId = [self.locationData.locationCallbacks pop];
    [super writeJavascript:[result toErrorCallbackString:callbackId]];
}

//- (void)locationManager:(CLLocationManager *)manager didStartMonitoringForRegion:(CLRegion *)region {
//    NSMutableDictionary* posError = [NSMutableDictionary dictionaryWithCapacity:2];
//    [posError setObject: [NSNumber numberWithInt: CDVCommandStatus_OK] forKey:@"code"];
//    [posError setObject: region.identifier forKey: @"regionid"];
//    CDVPluginResult* result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:posError];
//    NSString *callbackId = [self.locationData.locationCallbacks pop];
//    [super writeJavascript:[result toSuccessCallbackString:callbackId]];
//}


@end
