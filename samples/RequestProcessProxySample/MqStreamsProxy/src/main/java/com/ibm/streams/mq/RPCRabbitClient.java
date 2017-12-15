package com.ibm.streams.mq;
/*
* Licensed Materials - Property of IBM
* Copyright IBM Corp. 2017  
*/

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import org.apache.http.HttpStatus;

import java.util.logging.Level;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

/**
 * <p>
 * RabbitMQ client that is making a request to the Streams application, the application 
 * is processing the requests with the RabbitMQRequestProcess() operator. The server
 * is expected to:
 * <ul>
 * <li>Quality of service set to 1:</li>
 * <li>Disabled the autoAck.</li>
 * <li>Ack should be sent after the processing is done and before the response is returned.</el>
 * </ul> 
 * </p>
 * Notes:.
 * <ul>
 * <li>Every client that pushes onto the queue creates a private response queue,
 * common bus over private bus back.</li>
 * <li>Dynamic number of servers willing to take the clients requests</li>
 * <li>Dynamic number of client willing to generate clients requests</li> 
 * <li>The processing of the response is done within a thread of RabbitMQ. When the
 * processing is done the results are returned via payload.putResponse(), the results
 * are picked up by the payload.getResponse(). The two calls are 'hiding' the usage of 
 * <i>BlockingQueue<String>()</i>.

 * </ul>

 * 
 * </p>
 * 
 * @author siegenth
 *
 */
public class RPCRabbitClient {
	
	static String ReplyQueueName = "rpc_queue";
	static String RequestQueueName = null;	
	static Channel ChannelMaster = null; 	
	static Connection ConnectionMaster = null;
	static Map<String, Payload> ActiveRequest = null;
	
	private Connection connection;
	private Channel channel;
	//private String requestQueueName = "rpc_queue";
	//private RPCMessageBus rmb = null;
	Logger logger = null;

	/**
	 * Constructor creates return message queue
	 * @param password 
	 * @param username 
	 * 
	 * @param defaultQueueName
	 *            well known over the entire system, all requests are going through
	 *            this queue.
	 * @param hostName
	 *            : server where the queue is located.
	 * @param portNum
	 *            : must need a port
	 * @throws IOException
	 * @throws TimeoutException
	 */
	public RPCRabbitClient(Payload payload) throws IOException, TimeoutException {
		logger = Logger.getLogger(getClass().getName());		    		
		
		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost(payload.queueHost);
		if ((payload.username != null) && (payload.password != null)) {
			factory.setUsername(payload.username);
			factory.setPassword(payload.password);
		}

		if (ChannelMaster == null) {
			ConnectionMaster = factory.newConnection();			
			ChannelMaster = ConnectionMaster.createChannel();			
			ReplyQueueName = ChannelMaster.queueDeclare().getQueue();
			RequestQueueName = payload.getRequestQueueName(); 
			ActiveRequest = new ConcurrentHashMap<String, Payload>();					
		}
		this.channel = ChannelMaster;
		this.connection = ConnectionMaster;
		//this.replyQueueName = ReplyQueueName;

		logger.log(Level.INFO, String.format("corrId: %s - Open connections replyQueue:%s", payload.correlationId, ReplyQueueName));
	}

	// Hold the active requests while they are being processed.
	ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	String consumerTag = null;  // reference to outstanding RabbitMQ request.
	String jsonTimeout = new String("{'statusCode':408,'method':'GET','response':{'block':'0','fill':'A','dup':'zero'},'contextPath':'/MqStreamsProxy','pathInfo':'/SmokeTest'}");

	/**
	 * <p>
	 * Send message to MQserver, the message includes the response queue that should
	 * be used as well as a correlationID. Wait on the the response queue, when a
	 * messages arrives, use the collelationId to determine if a prior response has
	 * been received - log and drop if that is the case.
	 * </p>
	 * <p>
	 * This code is expected to be on a blockable (J2EE Async Context provides this). 
	 * The MB request is sent, ..blockingQueue.take() suspends waiting for the results.
	 * The MB response arrives via the  handleDeliver(), where it is unpacked. The unpacked results 
	 * are returned to the waiting ..blockQueue.offer().
	 * </p>
	 * <p>
	 * Timeout, if response does not arrive soon enough. Thread removes entry from activeRequests, 
	 * and 'creates' a response, which goes back to the reqeustor. If/when the response 
	 * does arrive over the Queue it is logged and dropped since it cannot be found in activeRequerts.
	 * </p>
	 * 
	 * @param message
	 *            being sent.
	 * @return 
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public String call(Payload payload) throws IOException, InterruptedException {
		String message = payload.toJsonString();
		// Map<String, Payload> ActiveRequest = new ConcurrentHashMap<String, Payload>();		
		
		logger.log(Level.INFO, String.format("corrId:%s @call replyQueueName:%s", payload.correlationId, ReplyQueueName));
		logger.log(Level.INFO, String.format("+@call payload:%s", payload.toString()));
		logger.log(Level.INFO, String.format("+@call ActiveRequest.size:%d", ActiveRequest.size()));			

		//  
		ActiveRequest.put(payload.correlationId, payload);

		// Send request to Server
		AMQP.BasicProperties props = new AMQP.BasicProperties.Builder().correlationId(payload.correlationId).replyTo(ReplyQueueName).build();
		channel.basicPublish("", RequestQueueName, props, message.getBytes("UTF-8"));

		boolean autoAckResponse = true;
		consumerTag = payload.consumerTag = channel.basicConsume(ReplyQueueName, autoAckResponse, new DefaultConsumer(channel) {
			@Override
			public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties,
					byte[] body) throws IOException {
				logger.log(Level.INFO,  String.format("corrID: %s DLVR: enter handleDelivery ", properties.getCorrelationId()));
				if(!ActiveRequest.containsKey(properties.getCorrelationId())) {
					logger.log(Level.WARNING, String.format("corrID: %s DLVR: MISSED corrlationId correlation", properties.getCorrelationId()));		
					return;
				};
				Payload payload = ActiveRequest.remove(properties.getCorrelationId());
				
				if (payload == null || payload.isTimeout()) {
					if (payload == null) {
						logger.log(Level.WARNING, String.format("DLVR no ActiveRequest entiry for corrID:%s", properties.getCorrelationId() ));
					} else {
						logger.log(Level.WARNING, String.format("DLVR corrId:%s activeRequests:%d Dropping this response, already timed out.", payload.correlationId, ActiveRequest.size()));						
					}
					return;
				}
				logger.log(Level.INFO, String.format("corrId:%s - DLVR: activeRequests:%d consumerTag:%s", payload.correlationId, ActiveRequest.size(), consumerTag));					
				logger.log(Level.INFO, String.format("corrId:%s - DLVR Response body:%s", payload.correlationId, new String(body, "UTF-8")));
				// Return results to the waiting suspended asyncContext.
				payload.putResponse(new String(body, "UTF-8"));
			}
			// Stopped doing this, it seems to destroy the response queue.
			@Override
			public void handleCancelOk(String consumerTag) {
				logger.log(Level.WARNING, String.format("CAN: handleCancel, scan for consumerTag:%s ",  consumerTag));												
				for(String key: ActiveRequest.keySet()) {
					Payload payload = ActiveRequest.getOrDefault(key, null);
					if (payload != null) {
						if (payload.consumerTag == consumerTag) {
											payload.putTimeout();
							logger.log(Level.INFO, String.format("corrId:%s - CAN: handleCancel (after) consumerTag:%s ", payload.correlationId,  consumerTag));
							return;
						}
					}
				}
				logger.log(Level.WARNING, String.format("corrId:??????? - CAN: handleCancel, failed to locate consumerTag:%s ",  consumerTag));								
			}
		});
		logger.log(Level.INFO, String.format("corrId:%s - blocking wait start", payload.correlationId));
		// suspending asyncContext until response comes back with results. 
		return(payload.getResponse());
	}

	

	public void close() throws IOException, TimeoutException {
		ConnectionMaster.close();
		ChannelMaster.close();
		ConnectionMaster = null;
		logger.log(Level.INFO, String.format(" Close connections requestQueue:%s replyQueue:%s", RequestQueueName, ReplyQueueName));
	}

}
