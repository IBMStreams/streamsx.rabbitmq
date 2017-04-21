/* Generated by Streams Studio: March 26, 2014 2:09:26 PM EDT */
/*******************************************************************************
 * Copyright (C) 2015, MOHAMED-ALI SAID
 * All Rights Reserved
 *******************************************************************************/
package com.ibm.streamsx.messaging.rabbitmq;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeoutException;

import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.StreamingOutput;
import com.ibm.streams.operator.OperatorContext.ContextCheck;
import com.ibm.streams.operator.compile.OperatorContextChecker;
import com.ibm.streams.operator.logging.TraceLevel;
import com.ibm.streams.operator.model.OutputPortSet;
import com.ibm.streams.operator.model.OutputPorts;
import com.ibm.streams.operator.model.Parameter;
import com.ibm.streams.operator.model.PrimitiveOperator;
import com.ibm.streams.operator.state.ConsistentRegionContext;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;

import java.util.logging.Logger;

/**
 * This operator was originally contributed by Mohamed-Ali Said @saidmohamedali
 */
@OutputPorts(@OutputPortSet(cardinality = 1, optional = false, description = "Messages received from Kafka are sent on this output port."))
@PrimitiveOperator(name = "RabbitMQSource", description = RabbitMQSource.DESC)
public class RabbitMQSource extends RabbitMQBaseOper {

	private List<String> routingKeys = new ArrayList<String>();
	
	private final Logger trace = Logger.getLogger(RabbitMQSource.class
			.getCanonicalName());
	
	private Thread processThread;
	private String queueName = ""; //$NON-NLS-1$

	private String queueNameParameter = ""; //$NON-NLS-1$
	
	//consistent region checks
	@ContextCheck(compile = true)
	public static void checkInConsistentRegion(OperatorContextChecker checker) {
		ConsistentRegionContext consistentRegionContext = 
				checker.getOperatorContext().getOptionalContext(ConsistentRegionContext.class);
		if (consistentRegionContext != null){
			checker.setInvalidContext(Messages.getString("OP_CANNOT_BE_START_OF_CONSISTENT_REGION"), null); //$NON-NLS-1$
		}
	}
	
	@Override
	public synchronized void initialize(OperatorContext context)
			throws Exception {
		
		super.initialize(context);
		super.initSchema(getOutput(0).getStreamSchema());
		trace.log(TraceLevel.INFO, this.getClass().getName() + "Operator " + context.getName() //$NON-NLS-1$
				+ " initializing in PE: " + context.getPE().getPEId() //$NON-NLS-1$
				+ " in Job: " + context.getPE().getJobId()); //$NON-NLS-1$

		// produce tuples returns immediately, but we don't want ports to close
		createAvoidCompletionThread();

		processThread = getNewConsumerThread();

		processThread.setDaemon(false);
	}

	private Thread getNewConsumerThread() {
		return getOperatorContext().getThreadFactory().newThread(
				new Runnable() {

					@Override
					public void run() {
						try {
							produceTuples();
						} catch (Exception e) {
							e.printStackTrace();
							trace.log(TraceLevel.ERROR, e.getMessage());
						}
					}

				});
	}

	
	/**
	 * Notification that initialization is complete and all input and output
	 * ports are connected and ready to receive and submit tuples.
	 * 
	 * @throws Exception
	 *             Operator failure, will cause the enclosing PE to terminate.
	 */
	@Override
	public synchronized void allPortsReady() throws Exception {
				
		processThread.start();
		
	}

	private void bindAndSetupQueue() throws IOException {
		
		boolean createdQueue = initializeQueue(connection);
		
		// Only want to bind to routing keys or exchanges if we created the queue
		// We don't want to modify routing keys of existing queues. 
		if (createdQueue){
			if (routingKeys.isEmpty())
				routingKeys.add("");//add a blank routing key //$NON-NLS-1$

			//You can't bind to a default exchange
			if (!usingDefaultExchange){
				for (String routingKey : routingKeys){
					channel.queueBind(queueName, exchangeName, routingKey);
					trace.log(TraceLevel.INFO, "Queue: " + queueName + " Exchange: " + exchangeName + " RoutingKey " + routingKey); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				}
			}
		} else {
			if (!routingKeys.isEmpty()) {
				trace.log(
						TraceLevel.WARNING,
						"Queue already exists, therefore specified routing key arguments have been ignored. " //$NON-NLS-1$
								+ "To use specified routing key/keys, you must either configure the existing queue " //$NON-NLS-1$
								+ "to bind to those keys, or restart this operator using a queue that does not already exist."); //$NON-NLS-1$
			}
		}
	}

	//this function returns true if we create a queue, false if we use a queue that already exists
	private boolean initializeQueue(Connection connection) throws IOException {
		boolean createdQueue = true;
		
		if (queueNameParameter.isEmpty()) {
			queueName = channel.queueDeclare().getQueue();
		} else {
			queueName = queueNameParameter;
			try {
				channel.queueDeclarePassive(queueName);
				trace.log(TraceLevel.INFO, "Queue was found, therefore no queue will be declared and user queue configurations will be ignored."); //$NON-NLS-1$
				createdQueue = false;
			} catch (IOException e) {
				channel = connection.createChannel();
				channel.queueDeclare(queueName, false, false, true, null);
				trace.log(TraceLevel.INFO, "Queue was not found, therefore non-durable, auto-delete queue will be declared."); //$NON-NLS-1$
			}
		}
		return createdQueue;
	}

	
	/**
	 * Submit new tuples to the output stream
	 * @throws InterruptedException 
	 * @throws IOException 
	 * @throws MalformedURLException 
	 * @throws Exception 
	 */
	private void produceTuples() throws MalformedURLException, IOException, InterruptedException, Exception{
		
		// After all the ports are ready, but before we start 
		// sending messages, setup our connection, channel, exchange and queue
		super.initializeRabbitChannelAndConnection();		
		bindAndSetupQueue();
		
		DefaultConsumer consumer = getNewDefaultConsumer();
		channel.basicConsume(queueName, true, consumer);
		
		while (!Thread.interrupted()){
			isConnected.waitForMetricChange();
			if (isConnected.getValue() != 1
					&& newCredentialsExist()){
				trace.log(TraceLevel.WARN,
						"New properties have been found so the client is restarting."); //$NON-NLS-1$
				resetRabbitClient();
				consumer = getNewDefaultConsumer();
				channel.basicConsume(queueName, true, consumer);
			}
		}
	}
	
	@Override
	public void resetRabbitClient() throws KeyManagementException, MalformedURLException, NoSuchAlgorithmException,
			URISyntaxException, IOException, TimeoutException, InterruptedException, Exception {
		if (super.getAutoRecovery()) {
			super.resetRabbitClient();
			bindAndSetupQueue();
		} 
	}

	private DefaultConsumer getNewDefaultConsumer() {
		return new DefaultConsumer(channel) {
			@Override
			public void handleDelivery(String consumerTag, Envelope envelope,
					AMQP.BasicProperties properties, byte[] body)
					throws IOException {
				if (isConnected.getValue() == 0){
					// We know we are connected if we're sending messages
					isConnected.setValue(1);
				}
				StreamingOutput<OutputTuple> out = getOutput(0);
				OutputTuple tuple = out.newTuple();

				messageAH.setValue(tuple, body);
				
				if (routingKeyAH.isAvailable()) {
					tuple.setString(routingKeyAH.getName(),
							envelope.getRoutingKey());
					if (trace.isLoggable(TraceLevel.DEBUG))
						trace.log(TraceLevel.DEBUG, routingKeyAH.getName() + ":" //$NON-NLS-1$
								+ envelope.getRoutingKey());
				} 				
				
				if (messageHeaderAH.isAvailable()){
					Map<String, Object> msgHeader = properties.getHeaders();
					if (msgHeader != null && !msgHeader.isEmpty()){
						Map<String, String> headers = new HashMap<String,String>();
						Iterator<Entry<String,Object>> it = msgHeader.entrySet().iterator();
						while (it.hasNext()){
							Map.Entry<String, Object> pair = it.next();
							if (trace.isLoggable(TraceLevel.DEBUG))
								trace.log(TraceLevel.DEBUG, "Header: " + pair.getKey() + ":" + pair.getValue().toString()); //$NON-NLS-1$ //$NON-NLS-2$
							headers.put(pair.getKey(), pair.getValue().toString());
						}
						tuple.setMap(messageHeaderAH.getName(), headers);
					}
				}

				// Submit tuple to output stream
				try {
					out.submit(tuple);
				} catch (Exception e) {
					trace.log(TraceLevel.ERROR, "Catching submit exception" + e.getMessage()); //$NON-NLS-1$
					e.printStackTrace();
				}
			}
			
			@Override
			public void handleCancelOk(String consumerTag) {
				trace.log(TraceLevel.INFO,"Recieved cancel signal at consumer"); //$NON-NLS-1$
				super.handleCancelOk(consumerTag);
			}
			
			@Override
			public void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {
				trace.log(TraceLevel.INFO,"Recieved shutdown signal at consumer"); //$NON-NLS-1$
				super.handleShutdownSignal(consumerTag, sig);
			}
		};
	}
	
	@Parameter(optional = true, description = "Routing key/keys to bind the queue to. If you are connecting to an existing queue, these bindings will be ignored.")
	public void setRoutingKey(List<String> values) {
		if(values!=null)
			routingKeys.addAll(values);
	}	
	
	@Parameter(optional = true, description = "Name of the queue. Main reason to specify is to facilitate parallel consuming. If this parameter is not specified, a queue will be created using a randomly generated name.")
	public void setQueueName(String value) {
		queueNameParameter = value;
	}
	
	@Parameter(optional = true, description = "Name of the RabbitMQ exchange to bind the queue to. If consuming from an already existing queue, this parameter is ignored. To use default RabbitMQ exchange, do not specify this parameter or use empty quotes: \\\"\\\".")
	public void setExchangeName(String value) {
		exchangeName = value;
	}

	/**
	 * Shutdown this operator, which will interrupt the thread executing the
	 * <code>produceTuples()</code> method.
	 * @throws Exception 
	 */
	public synchronized void shutdown() throws Exception {	
		if (processThread != null) {
			processThread.interrupt();
			processThread = null;
		}
		OperatorContext context = getOperatorContext();
		trace.log(TraceLevel.ALL, "Operator " + context.getName() //$NON-NLS-1$
				+ " shutting down in PE: " + context.getPE().getPEId() //$NON-NLS-1$
				+ " in Job: " + context.getPE().getJobId()); //$NON-NLS-1$
		// Must call super.shutdown()
		super.shutdown();
	}
	
	public static final String DESC = 
			"This operator acts as a RabbitMQ consumer, pulling messages from a RabbitMQ broker. " +  //$NON-NLS-1$
			"The broker is assumed to be already configured and running. " + //$NON-NLS-1$
			"The outgoing stream can have three attributes: message, routing_key, and messageHeader. " + //$NON-NLS-1$
			"The message is a required attribute. " + //$NON-NLS-1$
			"The exchange name, queue name, and routing key can be specified using parameters. " + //$NON-NLS-1$
			"If a specified exchange does not exist, it will be created as a non-durable exchange. " +  //$NON-NLS-1$
			"If a queue name is specified for a queue that already exists, all binding parameters (exchangeName and routing_key) " +  //$NON-NLS-1$
			"will be ignored. Only queues created by this operator will result in exchange/routing key bindings. " +  //$NON-NLS-1$
			"All exchanges and queues created by this operator are non-durable and auto-delete." +  //$NON-NLS-1$
			"This operator supports direct, fanout, and topic exchanges. It does not support header exchanges. " +  //$NON-NLS-1$
			"\\n\\n**Behavior in a Consistent Region**" +  //$NON-NLS-1$
			"\\nThis operator cannot participate in a consistent region." +  //$NON-NLS-1$
			BASE_DESC
			;
}