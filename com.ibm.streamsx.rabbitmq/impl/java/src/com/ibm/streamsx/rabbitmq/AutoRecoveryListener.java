package com.ibm.streamsx.rabbitmq;

import java.util.logging.Logger;

import com.ibm.streams.operator.logging.TraceLevel;
import com.rabbitmq.client.Recoverable;
import com.rabbitmq.client.RecoveryListener;

public class AutoRecoveryListener implements RecoveryListener {

	private final Logger trace = Logger.getLogger(this.getClass().getCanonicalName());
	
	// We use the base operator class to update metrics values
	private RabbitMQBaseOper	rabbitMQOp	= null;
	

	
	/**
	 * Initializes the listener with the operator.
	 * @param op	The RabbitMQ operator (Source or Sink) to handle exceptions for.
	 */
	public AutoRecoveryListener(RabbitMQBaseOper op) {
		this.rabbitMQOp = op;
	}
	
	
	@Override
	public void handleRecovery(Recoverable arg0) {
		trace.log(TraceLevel.INFO, "Recovered RabbitMQ connection."); //$NON-NLS-1$
		rabbitMQOp.setIsConnectedValue(1);
	}


	@Override
	public void handleRecoveryStarted(Recoverable arg0) {
		trace.log(TraceLevel.INFO, "Starting to recover RabbitMQ connection."); //$NON-NLS-1$
	}

}
