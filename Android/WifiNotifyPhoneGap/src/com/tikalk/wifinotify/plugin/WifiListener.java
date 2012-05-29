package com.tikalk.wifinotify.plugin;

import java.util.List;

import org.apache.cordova.api.PluginResult;
import org.apache.cordova.api.PluginResult.Status;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.util.Log;
import android.webkit.WebSettings.PluginState;

import com.phonegap.api.Plugin;
import com.tikalk.tools.DBTool;
import com.tikalk.tools.Defined;
import com.tikalk.tools.PendingEvent;
import com.tikalk.tools.Shared;
import com.tikalk.wifilistener.LocationSingleUpdateBroadcastReceiver;
import com.tikalk.wifilistener.WifiListenerService;
import com.tikalk.wifilistener.WifiListenerService.LocalBinder;


/**
 * NOTE ABOUT MULTIPLE CHECKIN
 * THIS PLUGGIN ASSUMES ONLY ONE PROJECT CAN BE CHECKED IN IN AT A TIME
 * IF THERE IS A REQUEST TO CHECKIN A PROJECT(PROJB) AND ANOTHER PROJECT(PROJA)
 * IS ALREADY CHECKED IN THEN THE REQUEST WILL FAIL AND THE ORIGIONAL PROJECT(PROJA) 
 * WILL REMAIN CHECKED IN
 */
public class WifiListener extends Plugin {
	/**
	 * CONSTANTS
	 */
	public static final String TAG = WifiListener.class.getName();

	//ACTIONS
	//start up the monitoring service
	public static final String ACTION_START = "start";
	//stop the monitoring service
	public static final String ACTION_STOP = "stop";
	//returns if service is running or not
	public static final String ACTION_SERVICE_RUNNING = "service_running";
	//get all active wifi spots
	public static final String ACTION_GET_ACTIVE = "get_active";
	//set point for tracking
	public static final String ACTION_ADD_PROJECT = "addProject";
	//confirm to login to a place
	public static final String ACTION_CHECKIN = "checkinToProject";
	//confirm to logout of a place
	public static final String ACTION_CHECKOUT = "checkoutOfProject";
	//requests the details for the notificaiton click
	public static final String ACTION_GET_NOTIFY_DETAILS = "get_notify_details";
	//requests the formatted log for logins and logouts
	public static final String ACTION_RETRIEVE_EVENTS_FOR_DATE = "retrieveProjectEventsForDate";
	//request for current wifi state
	public static final String ACTION_GET_WIFI_STATE = "get_wifi_state";
	//request to change wifi state
	public static final String ACTION_SET_WIFI_STATE = "set_wifi_state";
	//request project notification state
	public static final String ACTION_GET_SHOULD_AUTO_UPDATE = "getShouldAutoUpdateProjectEvents";
	//set project notification state
	public static final String ACTION_SET_SHOULD_AUTO_UPDATE = "setShouldAutoUpdateProjectEvents";
	//remove a project from 
	public static final String ACTION_REMOVE_PROJECT = "removeProject";

	//KEY CONSTANTS
	public static final String KEY_PROJECT_ID = "fid";
	public static final String KEY_SHOULD_UPDATE_BOOL = "shouldautoupdate";
	public static final String KEY_CURRENTLY_HERE_BOOL = "currentlyhere";
	public static final String KEY_LATITUDE = "latitude";
	public static final String KEY_LONGITUDE = "longitude";
	public static final String KEY_PROJECT_NAME = "projectname";

	public static final String KEY_DATE_YEAR = "year";
	public static final String KEY_DATE_MONTH = "month";
	
	//internal keys
	public static final String KEY_TIMESTAMP_START = "checkin";
	public static final String KEY_TIMESTAMP_STOP = "checkout";
	
	public static final String KEY_LOGGED_IN_BOOL = "loggedinbool";

	@Override
	public PluginResult execute(String action, JSONArray data, String callBackID) {
		PluginResult result = null;
		Context context = ctx.getApplicationContext();
		//create dbtool incase of need, and make sure to close before each return call
		DBTool db = new DBTool(context);
		if(action.matches(ACTION_START)){
			//check if service is started
			if(!WifiListenerService.isServiceRunning(context)){
				//start service
				Intent startServiceIntent = new Intent(context, WifiListenerService.class);
				context.startService(startServiceIntent);
				db.close();
				//returns true since starting service
				return (new PluginResult(Status.OK, true));
			}
			db.close();
			//returns false because service already started
			return (new PluginResult(Status.OK, false));



		}
		else if(action.matches(ACTION_STOP)){
			if(WifiListenerService.isServiceRunning(ctx.getApplicationContext())){

				//stop service
				Intent stopServiceIntent = new Intent(context, WifiListenerService.class);
				context.bindService(stopServiceIntent, new ServiceConnection() {

					public void onServiceDisconnected(ComponentName name) {
						// TODO Auto-generated method stub

					}

					public void onServiceConnected(ComponentName name, IBinder service) {
						WifiListenerService wifiService = ((WifiListenerService.LocalBinder)service).getService();
						wifiService.unregisterRecievers();
						wifiService.stopSelf();

					}
				}, Context.BIND_NOT_FOREGROUND);
				//boolean serviceStoppedBool = context.stopService(stopServiceIntent);
				db.close();
				//returns true since starting service
				return (new PluginResult(Status.OK, true));
			}
			db.close();
			//returns false because service already stopped
			return (new PluginResult(Status.OK, false));

		}
		else if(action.matches(ACTION_SERVICE_RUNNING)){
			boolean running = WifiListenerService.isServiceRunning(ctx.getApplicationContext());
			db.close();
			return (new PluginResult(Status.OK, running));
		}
		else if(action.matches(ACTION_GET_ACTIVE)){
			//build list
			List<String> list = Shared.getCurrentSpots();
			JSONArray jsonListArray = new JSONArray();
			//build json objects
			int size = list.size();
			for(int i=0; i< size; i++){
				JSONObject newPoint = new JSONObject();
				String bssid = list.get(i);
				try {
					newPoint.put(Defined.KEY_PROJECT_NAME, bssid);
					newPoint.put(Defined.KEY_EXISTS, db.existsBSSID(bssid));
					newPoint.put(Defined.KEY_LOGGED_IN, db.isLoggedIn(bssid));
				} catch (JSONException e) {
					db.close();
					return new PluginResult(Status.ERROR, getJsonObjectError(e));
				}
				jsonListArray.put(newPoint);

			}
			db.close();
			return(new PluginResult(Status.OK,jsonListArray));

		}
		else if(action.matches(ACTION_CHECKIN)){
			db.close();
			return log(true, data, context);
		}
		else if(action.matches(ACTION_CHECKOUT)){
			db.close();
			return log(false, data, context);
		}
		else if(action.matches(ACTION_ADD_PROJECT)){
			//grab all the local bssids and add to database with given info
			List<String> current = Shared.getCurrentSpots();
			int size = current.size();
			//init json data variables
			String name = "";
			String id = "";
			String latitude = "";
			String longitude = "";
			//grab hotspot info from json
			try {
				name = data.getJSONObject(0).getString(KEY_PROJECT_NAME);
				id = data.getJSONObject(0).getString(KEY_PROJECT_ID);
				latitude =  data.getJSONObject(0).getString(KEY_LATITUDE);
				longitude =  data.getJSONObject(0).getString(KEY_LONGITUDE);

			}catch (JSONException e) {
				Log.d(TAG, "error setting new point:" + e.getMessage());
				db.close();
				return new PluginResult(Status.ERROR, getJsonObjectError(e));
			}
			//boolean for if add passes or fails (if not spots then false)
			boolean addPassed = size > 0;
			for(int i=0; i < size; i++){
				String bssid = current.get(i);

				//String ssid = data.getJSONObject(0).getString("ssid");


				if(bssid.matches("")){
					db.close();
					return new PluginResult(Status.ERROR, getJsonObjectError(new JSONException("no ssid")));
				}
				/*//add hotspot to queue
				Shared.queueAddEvent(new PendingEvent(ssid, PendingEvent.EVENT_ADD_POINT));	
				LocationSingleUpdateBroadcastReceiver.startSingleUpdate(context);*/
				addPassed &= db.addPoint(id, bssid, name, longitude, latitude);

			}
			//only add project to project db if all previous adds passed
			if(addPassed)
				addPassed &= db.addProject(id, name);
			result = new PluginResult(addPassed?Status.OK:Status.ERROR);
			db.close();
		}
		else if(action.matches(ACTION_GET_NOTIFY_DETAILS)){
			JSONObject retVal = new JSONObject();
			try {
				retVal.put(Defined.KEY_PROJECT_NAME, Shared.getProjectName());
				retVal.put(Defined.KEY_LOGGING_IN, Shared.mLoggingIn);
				retVal.put(Defined.KEY_PROJECT_ID, Shared.getProjectID());
			} catch (JSONException e) {
				db.close();
				return  new PluginResult(Status.ERROR, getJsonObjectError(e));
			}
			db.close();
			return new PluginResult(Status.OK, retVal);
		}

		else if(action.matches(ACTION_RETRIEVE_EVENTS_FOR_DATE)){
			//grab month and year from the caller and pass to dbtool
			int year, month;
			try {
				year = data.getJSONObject(0).getInt(KEY_DATE_YEAR);
				month = data.getJSONObject(0).getInt(KEY_DATE_MONTH);
			} catch (JSONException e) {
				// invalid values
				db.close();
				return  new PluginResult(Status.ERROR, getJsonObjectError(e));
			}
			//convert year to 1900=0 format and month to jan=0 format
			year -= 1900;
			month--;
			JSONArray logDetails = db.getAllTimeStamps(year, month);
			db.close();
			//if failed then return error
			if(logDetails == null){
				db.close();
				return new PluginResult(Status.ERROR, "returned null from DB");
			}
			
			db.close();
			return new PluginResult(Status.OK, logDetails);
		}
		else if(action.matches(ACTION_GET_WIFI_STATE)){
			WifiManager wMNG = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
			//close db
			db.close();
			return new PluginResult(Status.OK, wMNG.isWifiEnabled());
		}
		else if(action.matches(ACTION_SET_WIFI_STATE)){
			//grab boolean param
			Boolean state;
			try {
				state = data.getJSONObject(0).getBoolean("state");
			} catch (JSONException e) {
				db.close();
				return  new PluginResult(Status.ERROR, getJsonObjectError(e));
			}
			WifiManager wifiManager = (WifiManager)ctx.getSystemService(Context.WIFI_SERVICE);
			wifiManager.setWifiEnabled(state);
			//close db
			db.close();
			//return current wifi state
			return new PluginResult(Status.OK, wifiManager.isWifiEnabled());
			
		}
		else if(action.matches(ACTION_GET_SHOULD_AUTO_UPDATE)){
			String projectID;
			JSONObject retVal = new JSONObject();
			try {
				projectID = data.getJSONObject(0).getString(KEY_PROJECT_ID);
				retVal.put(KEY_SHOULD_UPDATE_BOOL, db.getAutoUpdate(projectID));
				retVal.put(KEY_CURRENTLY_HERE_BOOL, db.isLoggedIn(projectID));
			} catch (JSONException e) {
				db.close();
				return  new PluginResult(Status.ERROR, getJsonObjectError(e));
			}
			
			db.close();
			return new PluginResult(Status.OK,retVal);
		}
		else if(action.matches(ACTION_SET_SHOULD_AUTO_UPDATE)){
			String projectID;
			boolean shouldAuto;
			try {
				projectID = data.getJSONObject(0).getString(KEY_PROJECT_ID);
				shouldAuto = data.getJSONObject(0).getBoolean(KEY_SHOULD_UPDATE_BOOL);
				
			} catch (JSONException e) {
				db.close();
				return  new PluginResult(Status.ERROR, getJsonObjectError(e));
			}
			boolean boolResult = db.setAutoUpdate(shouldAuto, projectID);
			db.close();
			return new PluginResult(Status.OK,boolResult);
		}
		else if(action.matches(ACTION_REMOVE_PROJECT)){
			String projectID = "";
			
			try {
				projectID = data.getJSONObject(0).getString(KEY_PROJECT_ID);
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if(db.removeProject(projectID)){
				db.close();
				return new PluginResult(Status.OK);
			}
			else{
				db.close();
				return new PluginResult(Status.ERROR, "Failed to remove");
			}
		}



		if(result == null)
			result = new PluginResult(Status.ERROR, getJsonObjectError(new JSONException("no value")));
		db.close();
		return result;
	}

	private JSONObject getJsonObjectError(JSONException e) {
		JSONObject retVal = new JSONObject();
		try {
			retVal.put(Defined.KEY_ERR_MESSAGE, e.getMessage());
			retVal.put(Defined.KEY_ERR_STACK, e.getStackTrace().toString());
		} catch (JSONException e1) {
			e1.printStackTrace();
		}
		return retVal;
	}

	private PluginResult log(boolean login, JSONArray data, Context context){
		//grab database
		DBTool db = new DBTool(context);
		try {
			String projectID = data.getJSONObject(0).getString(KEY_PROJECT_ID);
			if(projectID.matches("")){
				db.close();
				return new PluginResult(Status.ERROR);
			}
			//set new login status
			boolean success = db.setLoggedIn(login, projectID);
			//login passes new status in tracker
			if(success)
				db.setLoggedInTimestamp(login, projectID);
			db.close();
			if(success)
				return new PluginResult(Status.OK);
			else
				return new PluginResult(Status.ERROR);

		} catch (JSONException e) {
			Log.d(TAG, "error logging:" + e.getMessage());
			db.close();
			return new PluginResult(Status.ERROR);
		}
	}
}
