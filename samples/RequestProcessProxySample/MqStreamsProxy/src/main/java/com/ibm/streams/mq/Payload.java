package com.ibm.streams.mq;
/*
* Licensed Materials - Property of IBM
* Copyright IBM Corp. 2017  
*/
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;

/**
 * <p>
 * Handles the request encoding of data that is being exchanged with 
 * Streams. This may just be a copy of the request.
 * </p> 
 * <p>
 * Using the Gson field tagging (Expose), fields
 * with the tag will be encoded. If you want the field
 * to be send then you need to use '@Expose'
 * </p>
 *
 * @author siegenth
 *
 */
public class Payload {
	@Expose private String request;
	@Expose private int sequenceNumber;
	@Expose public int sessionId;
	@Expose private String contextPath; 
	@Expose private String pathInfo;
	@Expose private String method;  
	@Expose private Map<String, String> headers;  	
	
    private BlockingQueue<String> blockingQueue = null;
	private String requestQueueName;
	public String consumerTag;
	public AsyncContext asyncContext = null;
	public String correlationId; 
	boolean timeout = false;
	boolean error = false;	
	
	String queueHost = null;
	String username = null;
	String password = null;
	Logger logger = null;
	private String errorMessage;
	public RPCAsyncMonitor rpcServletListener;
    
    private static int globalSequence = 0; 
    
    public Payload() {
		sequenceNumber = Payload.globalSequence++;
        blockingQueue = new ArrayBlockingQueue<String>(1);
        Random random = new Random();
        sessionId = random.nextInt((50-10)) + 10;
        correlationId = UUID.randomUUID().toString();
		logger = Logger.getLogger(getClass().getName());		
    }
    
	public String getQueueHost() {
		return queueHost;
	}

	public String getPassword() {
		return password;
	}

	public String getUserame() {
		return username;
	}

	public Payload(Gson gson) {
		// is this necessary - for loading back. 
	}
	/**
	 * Load the payload from the the httpRequest, make a distinction between GET,POST...  
	 * @param httpRequest
	 */
	private void load(HttpServletRequest httpRequest) {
		
		this.method = httpRequest.getMethod();		
		this.request = httpRequest.getQueryString();
		this.contextPath = httpRequest.getContextPath();
		this.pathInfo = httpRequest.getPathInfo();
		headers = new HashMap<String, String>();
		for (Enumeration<String>names = httpRequest.getHeaderNames(); names.hasMoreElements();) {
			String name = names.nextElement();
			headers.put(name, httpRequest.getHeader(name));
			logger.log(Level.INFO, String.format("corrId:%s request header name:%s value:%s", correlationId, name, httpRequest.getHeader(name)));
		}	
	}	
	
	public Payload(HttpServletRequest httpRequest, Map pathQueueMap, String defaultRequestQueue, String queueHost, String username, String password) {
		this();
		load(httpRequest);
		this.requestQueueName = (String) pathQueueMap.getOrDefault(this.pathInfo, defaultRequestQueue);
		this.queueHost = queueHost;
		this.username = username;
		this.password = password;
		
	}
	
	/**
	 * Queue that the message will be sent out on, the default may have been overridden.
	 * 
	 * @return the queue that the request should be sent to. 
	 */
	public String getRequestQueueName() {
		return this.requestQueueName;
	}
	/**
	 * Extract the fields that are to be sent to server, using the 'Expose' annotation 
	 * to get only what is necessary. 

	 * @return String that can be converted to Json to be transmitted. 
	 */
	public String toJsonString() {
		GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.disableHtmlEscaping();
		gsonBuilder.excludeFieldsWithoutExposeAnnotation();
		Gson gson = gsonBuilder.create();		
		return(gson.toJson(this));	
	}
	public void putError(String errorMessage) {
		this.errorMessage = errorMessage;
		this.error = true;
		logger.log(Level.SEVERE, String.format("corrId:%s - Error:%s", correlationId, errorMessage ));		
		blockingQueue.offer("");		
	}
	public void putTimeout() {
		this.timeout = true;
		logger.log(Level.WARNING, String.format("corrId:%s - Timeout, forcing completion of blockingQueue.", correlationId ));		
		blockingQueue.offer("");
	}
	public boolean isTimeout() {
		return this.timeout;
	}
	/**
	 * Returns true if we are having issues.
	 * @return
	 */
	public boolean isProblem() {
		return this.timeout | this.error;
	}
	public String toString() {
		return String.format("corrId:%s requestQueue:%s message:%s ", correlationId, getRequestQueueName(), toJsonString());
	}

	public void putResponse(String body) {
		blockingQueue.offer(body);
	}
	public String getResponse() throws InterruptedException {
		return(blockingQueue.take());
	}
}
