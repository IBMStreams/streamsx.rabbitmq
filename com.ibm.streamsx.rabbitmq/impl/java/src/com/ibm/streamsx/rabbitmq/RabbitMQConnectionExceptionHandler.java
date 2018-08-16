package com.ibm.streamsx.rabbitmq;

import java.util.logging.Logger;

import com.ibm.streams.operator.logging.TraceLevel;
import com.ibm.streamsx.rabbitmq.i18n.Messages;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.ExceptionHandler;
import com.rabbitmq.client.TopologyRecoveryException;

public class RabbitMQConnectionExceptionHandler implements ExceptionHandler {
	
	private final Logger		trace		= Logger.getLogger(this.getClass().getCanonicalName());
	
	// We use the base operator class to update metrics values
	private RabbitMQBaseOper	rabbitMQOp	= null;
	

	
	/**
	 * Initializes the exception handler with the operator 
	 * to handle the exceptions for.
	 * @param op	The RabbitMQ operator (Source or Sink) to handle exceptions for.
	 */
	public RabbitMQConnectionExceptionHandler(RabbitMQBaseOper op) {
		this.rabbitMQOp = op;
	}
	
	@Override
	public void handleBlockedListenerException(Connection arg0, Throwable arg1) {
		arg1.printStackTrace();
		trace.log(TraceLevel.ERROR, Messages.getString("SEE_STDOUT_FOR_FULL_STACK_TRACE", arg1.getMessage())); //$NON-NLS-1$
	}

	@Override
	public void handleChannelRecoveryException(Channel arg0, Throwable arg1) {
		arg1.printStackTrace();
		trace.log(TraceLevel.ERROR, Messages.getString("SEE_STDOUT_FOR_FULL_STACK_TRACE", arg1.getMessage())); //$NON-NLS-1$
	}

	@Override
	public void handleConfirmListenerException(Channel arg0, Throwable arg1) {
		arg1.printStackTrace();
		trace.log(TraceLevel.ERROR, Messages.getString("SEE_STDOUT_FOR_FULL_STACK_TRACE", arg1.getMessage())); //$NON-NLS-1$
	}

	@Override
	public void handleConnectionRecoveryException(Connection arg0, Throwable arg1) {
		arg1.printStackTrace();
		trace.log(TraceLevel.ERROR, Messages.getString("SEE_STDOUT_FOR_FULL_STACK_TRACE", arg1.getMessage())); //$NON-NLS-1$
		rabbitMQOp.setIsConnectedValue(0);
	}

	@Override
	public void handleConsumerException(Channel arg0, Throwable arg1, Consumer arg2, String arg3, String arg4) {
		arg1.printStackTrace();
		trace.log(TraceLevel.ERROR, Messages.getString("SEE_STDOUT_FOR_FULL_STACK_TRACE", arg1.getMessage())); //$NON-NLS-1$
	}

	@Override
	public void handleReturnListenerException(Channel arg0, Throwable arg1) {
		arg1.printStackTrace();
		trace.log(TraceLevel.ERROR, Messages.getString("SEE_STDOUT_FOR_FULL_STACK_TRACE", arg1.getMessage())); //$NON-NLS-1$
	}

	@Override
	public void handleTopologyRecoveryException(Connection arg0, Channel arg1, TopologyRecoveryException arg2) {
		arg2.printStackTrace();
		trace.log(TraceLevel.ERROR, Messages.getString("SEE_STDOUT_FOR_FULL_STACK_TRACE", arg2.getMessage())); //$NON-NLS-1$
	}

	@Override
	public void handleUnexpectedConnectionDriverException(Connection arg0, Throwable arg1) {
		arg1.printStackTrace();
		trace.log(TraceLevel.ERROR, Messages.getString("SEE_STDOUT_FOR_FULL_STACK_TRACE", arg1.getMessage())); //$NON-NLS-1$
		rabbitMQOp.setIsConnectedValue(0);
	}

}
