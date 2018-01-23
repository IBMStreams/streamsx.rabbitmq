package com.ibm.streams.mq;
/*
* Licensed Materials - Property of IBM
* Copyright IBM Corp. 2017  
*/
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
/**
 * <p>
 * Extract fields from JSON that will be built into response back to the web requester. 
 * It's necessary to do discovery, if the fields have been included and format them
 * for the response. 
 * </p><p>
 * For example, the results to the web request is transmitted as String but it may have 
 * been formatted JSON object, extract the JSON object as string and transmit it.  
 * The receiver can interpret it as JSON object. 
 * </p>
 */

public class ResponseViaJson {

	Gson gsonObj = new Gson();
	JsonParser parser = null;
	com.google.gson.JsonObject obj;
	public ResponseViaJson(String jsonString) { 
		parser = new JsonParser();
		obj = parser.parse(jsonString).getAsJsonObject();		
	}
	
	public int extractInt(String fieldName, int defaultValue ) {
		int value = defaultValue;  
        if ((obj.get(fieldName) != null) && obj.get(fieldName).isJsonPrimitive()) {
    			value = obj.get(fieldName).getAsInt();  
        }
        return(value);
	}
	/**
	 * Get around the issue where the result field could contain a list, object or string.
	 * Extract of the field you need to know the type in advance. I don't want to  
	 * impose this restriction so I need to do discovery.  
	 * 
	 *  @return String : that is valid to respond with. 
	 */
	public String extractString(String fieldName) {
		String str = "";
		if (obj.get(fieldName) != null) {
			if (obj.get(fieldName).isJsonObject()) {
				str = obj.get(fieldName).getAsJsonObject().toString();        	
			}
			if (obj.get(fieldName).isJsonPrimitive()) {
				str = obj.get(fieldName).getAsString();        	        	
			}
			if (obj.get(fieldName).isJsonArray())  {
				str = obj.get(fieldName).getAsJsonArray().toString();        	        	
			}
		}
		return str;
	}
	/**
	 * <p>
	 * Build as if it's and object. Put in alternative if it's a 
	 * List<List<String, String>> we should take care of this as well. 
	 * Field value can be a object or array of array[2]
	 * </p><p>
	 * Used for transmitting as header. 
	 * </p>
	 * @ return map with no elements if it fails 
	 */
	public Map<String, String> extractMap(String fieldName) {
		HashMap<String, String>map = new HashMap<String, String>();

		if (obj.get(fieldName) == null) {
			return(map);
		}
        if (obj.get(fieldName).isJsonObject()) {
            JsonObject jobj = obj.get(fieldName).getAsJsonObject();
            Set<String> fields = jobj.keySet();
            for (String field : fields) {
            		map.put(field,jobj.get(field).getAsString());
            }
        }

        if (obj.get(fieldName).isJsonArray())  {
        		JsonArray jarr = obj.getAsJsonArray(fieldName);
        		for (JsonElement jele : jarr) {
        			if(jele.isJsonArray()) {
        				map.put(jele.getAsJsonArray().get(0).toString(), jele.getAsJsonArray().get(1).toString());
        			} else {
        				return(new HashMap<String, String>());
        			}
        		}
        }
		return(map);
	}
	
	public static void main(String[] args) {
		// debugging tests 
		String rspV1 = new String("{'statusCode':10,'headers':{'Accept':'* *','sndHead2':'two','sndHead1':'one','Connection':'keep-alive','genHead1':'','genHead2':'','User_Agent':''},'method':'GET','response':{'block':'0','fill':'A','dup':'zero'},'contextPath':'/MqStreamsProxy','pathInfo':'/SmokeTest'}");
		String rspV2 = new String("{'statusCode':10,'headers':[['Accept','* *'],['sndHead2','two'],['sndHead1','one'],['Connection','keep-alive'],['genHead1',''],['genHead2',''],['User_Agent','']],'method':'GET','response':\"'block':'0','fill':'A','dup':'zero'\",'contextPath':'/MqStreamsProxy','pathInfo':'/SmokeTest'}");				
		String rspV3 = new String(
"{'sequenceNumber':10,'headers':[['Accept','* *'],['sndHead2','two'],['sndHead1','one'],['Connection','keep-alive'],['genHead1',''],['genHead2',''],['User_Agent','']],'method':'GET','response':['block0','fillA','dupzero'],'contextPath':'/MqStreamsProxy','pathInfo':'/SmokeTest'}");						
		ResponseViaJson jtr = new ResponseViaJson(rspV1);
		System.out.println(jtr.extractMap("headers"));
		System.out.println(jtr.extractString("response"));
		System.out.println(jtr.extractInt("statusCode", 200));		
	}
}

