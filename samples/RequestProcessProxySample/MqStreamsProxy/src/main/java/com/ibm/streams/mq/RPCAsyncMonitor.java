package com.ibm.streams.mq;
/*
* Licensed Materials - Property of IBM
* Copyright IBM Corp. 2017  
*/
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;

/**
 * Monitor of J2EE asynchronous processing.
 * <p>
 * Notification of state change passes through here. This is a good way to monitor 
 * the J2EE's asynchronous threads. a 
 * </p>
 * 
 * @author siegenth
 *
 */
public class RPCAsyncMonitor implements AsyncListener {
	Logger logger = null;
	Payload payload;

	public RPCAsyncMonitor(Payload payload) {
		logger = Logger.getLogger(getClass().getName());
		this.payload = payload;
	}

	@Override
	public void onComplete(AsyncEvent asyncEvent) throws IOException {
		if (payload.isTimeout()) {
			logger.log(Level.WARNING, String.format("corrId:%s ** onTimeout ending within onComplete", payload.correlationId));					
		} else {
			logger.log(Level.INFO, String.format("corrId:%s ** onComplete", payload.correlationId));
		}
	}
	/**
	 * Notify the possibly waiting thread that an error has occured. 
	 */
	@Override
	public void onError(AsyncEvent asyncEvent) throws IOException {
		logger.log(Level.SEVERE, String.format("corrId:%s ** onErr", payload.correlationId));
		payload.putError(asyncEvent.toString());
	}

	@Override
	public void onStartAsync(AsyncEvent asyncEvent) throws IOException {
		logger.log(Level.INFO, String.format("corrId:%s ** onStartSync", payload.correlationId));		
	}
	/**
	 * Notify the the possibliy waiting thread that an error has occured. 
	 */
	@Override
	public void onTimeout(AsyncEvent asyncEvent) throws IOException {
		logger.log(Level.INFO, String.format("corrId:%s ** onTimeout begin", payload.correlationId));		
		payload.putTimeout();
	}
}
