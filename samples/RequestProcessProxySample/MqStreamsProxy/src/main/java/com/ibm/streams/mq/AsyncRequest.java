package com.ibm.streams.mq;
/*
* Licensed Materials - Property of IBM
* Copyright IBM Corp. 2017  
*/
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletResponse;
import org.apache.http.HttpStatus;
/**
 * <p>
 * Send request via MessageBus (RabbitMq), invoked by J2EE aysncContext invocation which was developed
 * to enable servlets to be invoke asynchronously.    
 * </p>
 * <p>
 * The  <i>call(payload)</i> is making async invocation of RabbitMQ interface, it will not return until it 
 * has data or times out. 
 *</p> 
 * 
 */

class AsyncRequest implements Runnable {
	String str;

	Payload payload;
	AsyncContext asyncContext;
	RPCRabbitClient onLine;
	String remoteResponse;
	HttpServletResponse response;
	Logger logger = null;

	AsyncRequest(Payload payload, HttpServletResponse response) throws IOException, TimeoutException {
		this.payload = payload;
		this.asyncContext = payload.asyncContext;
		this.onLine = new RPCRabbitClient(payload);
		this.response = response;
		logger = Logger.getLogger(getClass().getName());
	}

	@Override
	public void run() {
		logger.log(Level.INFO,
				String.format("corrId:%s - J2EE start", payload.correlationId));

		try {// make call to RabbitMQ + wait for response			
			remoteResponse = onLine.call(payload); 
			logger.log(Level.INFO,
					String.format("corrId:%s - MessageBus request/response completed", payload.correlationId));	
		} catch (InterruptedException e) {
			logger.log(Level.SEVERE, String.format("corrId:%s - MessageBus InterruptedException: %s",
					payload.correlationId, e.getMessage()));
			e.printStackTrace();
		} catch (IOException e) {
			logger.log(Level.SEVERE,
					String.format("corrId:%s - MessageBus IOException : %s", payload.correlationId, e.getMessage()));
			e.printStackTrace();
		}
		//
		// compose response.
		if (payload.isProblem()) {
			logger.log(Level.WARNING, String.format("corrId:%s - Response timeout, dropping", payload.correlationId));
			return;
		}
		if (remoteResponse.length() == 0) {
			logger.log(Level.WARNING, String.format("corrId:%s - unexepected timout", payload.correlationId));
		}
		ResponseViaJson responseViaJson = new ResponseViaJson(remoteResponse);
		String responseContent = responseViaJson.extractString("response");

		int errCode = responseViaJson.extractInt("statusCode", HttpStatus.SC_OK);
		if (errCode != HttpStatus.SC_OK) {
			try {
				response.sendError(errCode);
			} catch (IOException e) {
				logger.log(Level.SEVERE, String.format("corrId:%s - Failed to send error code to requestor : %d:%s",
						payload.correlationId, errCode, responseContent));
				e.printStackTrace();
			}
			return;
		}
		response.setStatus(errCode);
		Map<String, String> headers = responseViaJson.extractMap("headers");
		for (String key : headers.keySet()) {
			logger.log(Level.FINE, "header transfer " + key + ":" + headers.get(key));
			response.addHeader(key, headers.get(key));
		}
		logger.log(Level.INFO, String.format("corrId:%s - Response %d:%s", payload.correlationId,
				responseContent.length(), responseContent));
		asyncContext.getResponse().setContentLength(responseContent.length());
		try {
			asyncContext.getResponse().getWriter().append(responseContent);
			asyncContext.getResponse().flushBuffer();
		} catch (IOException e) {
			logger.log(Level.SEVERE, String.format("corrId:%s - Web write failure: %s", payload.correlationId, e.getMessage())); 
			e.printStackTrace();
		}
		asyncContext.complete();
		/*
		try {
			onLine.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		*/
	}
}
