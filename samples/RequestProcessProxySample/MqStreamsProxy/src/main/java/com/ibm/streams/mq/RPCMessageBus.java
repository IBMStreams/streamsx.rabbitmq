package com.ibm.streams.mq;
/*
* Licensed Materials - Property of IBM
* Copyright IBM Corp. 2017  
*/
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.AsyncContext;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
// auto-revert-tail-modecodex
import org.apache.http.HttpStatus;
import com.google.gson.Gson;



/**
 * <p>
 * Message bus proxy servlet using RabbitMQ. This servlet accepts HTTP rest request and forwards via 
 * RabbitMQ to RabbitMQRequestResponse operator of RabbitMQ toolkit. The RabbitMQRequestResponse operator
 * enables a RPC. The diagram below illustrates RabbitMQReqeustResponse operator, accepting a request from
 * RabbitMQ, injecting it into the streams, waiting for the response returning it via RabbotMQ.
 * </p>
 * <pre>
 * 
 *               V-------CID--------<---------------<-------CID-------------<----------|
 *       ------------------       ----------------       ----------------         ----------------
 *  MQ > |RabbitMQRequest |       | Filter       |       | Aggregate    |         | Custom       |
 *       |  Process       |->CID>-|  Oper        |->CID>-|   Oper       |->-CID>--|  Formatting  |
 *  MQ < |   Oper         |       |              |       |              |         |    Operator  |
 *       ------------------       ----------------       ----------------         ----------------
 * </pre>
 * <p>
 * The example uses the RabbitMQ recommended RPC pattern of having the request include ResponseQueue and 
 * CorrelatioonId, these values are used to route the response back to the requester. The CorrelationId (CID) 
 * field accompanies the request as it's propagated through the stream, all the way back to the originating
 * MQReqeustRespone() for correlation reparation.  
 * </p>
 * <p>
 * This example has multiple Streams applications servicing multiple servlets through a 
 * well known queue. All requests arrive to streams via a well know queue, each servlet has a 
 * private queue that responses are routed through. RabbitMQ  will delivery the request to 
 * waiting process that is currently idle, in order for RabbitMQ to determine the idleness of 
 * an server stream, the server code receiving the receiving the RabbitMQ should :
 * <ul>
 * <li>Quality of service set to 1: <br><i>channel.basicQos(1, false)</i></li>
 * <li>Disabled the autoAck. : <br> <i>channel.basicConsume(queueName, false, consumer);</i></li>
 * <li>Ack should be sent after the processing is done and before the response is returned:<br> <i>channel.basicAck(rc.deliveryTag, false);</i></el>
 * </ul> 
 *<p>
 * Adding new processing elements, J2EE clients or stream sever applications, can be done independently. The arbitration 
 * of processing is done through the well know queue. 
 * <pre>
 *       -----------                         ---------------
 * web > | J2ee    | --<--responseQueue--<---|Streams      |
 *       |         |\                       /|  Server App |
 *       ----------- \                     / ---------------
 *                     =>=wellKnownQueue=>=  
 *       ----------- /                     \ ---------------
 *       | J2EE    |/                       \| Streams     |
 * web > |         |--<--responseQueue---<---|  Server App |
 *       -----------                         ---------------
 *<pre/>
 *<p>
 * Adding a new Streams server to the diagram above would distribute
 * the processing from the two J2EE clients from two streams server application
 * to three. The responseQueue that the server application uses 
 * is included in the request found in the wellKnownQueue. 
 * </p>
 * <b>
 * The set of annotations below can be overridden by the values in the web.xml
 * file at runtime.
 * </b>
 */
@WebServlet(name = "RPCMessageBus", asyncSupported = true, description = "Puts web request onto rpc message bus.", urlPatterns = {
		"/*" }, initParams = {
				@WebInitParam(name = "log", value = "true", description = "Enable logging that uses the log() method."),
				@WebInitParam(name = "defaultRequestQueue", value = "rpc_queue", description = "Common queue that all servers are read from."),
				@WebInitParam(name = "queueHost", value = "localhost", description = "Host where the Messaging system (RabbitMQ) is running. "),
				@WebInitParam(name = "username", value = "guest", description = "Username. "),
				@WebInitParam(name = "password", value = "guest", description = "Password. "),
				@WebInitParam(name = "timeout", value = "10000", description = "Miliseconds to wait for response from Streams before returning a timeout message."),				
				@WebInitParam(name = "port", value = "8088", description = "Port to listen for web requests ") })
public class RPCMessageBus extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final int TIMEOUT_DEFAULT = 10000;
	final static String LOG_CONFIG = "log";
	final static String DEFAULT_QUEUE_CONFIG = "defaultRequestQueue";
	final static String QUEUE_HOST_CONFIG = "queueHost";
	final static String PATHQUEUE_MAP_CONFIG = "pathQueueMap";
	final static String USERNAME_CONFIG = "username";
	final static String PASSWORD_CONFIG = "password";
	final static String TIMEOUT_CONFIG = "timeout";

	boolean doLog = true;
	String queueHost = null;
	Map<String, String> pathQueueMap = null;
	String defaultRequestQueue = null;
	String username = null;
	String password = null;
	int timeout = TIMEOUT_DEFAULT;
	Logger logger = null;

	RPCRabbitClient onLineGlob = null;
	/**
	 * Shutdown bus when the session Servlet is being shut down.
	 */
	private void busDown() throws ServletException {
		if (onLineGlob != null) {		
			try {
				onLineGlob.close();
			} catch (IOException e) {
				e.printStackTrace();
				throw new ServletException("Failed to shutdown / Exception : '" + defaultRequestQueue + "' :  " + e.getMessage());
			} catch (TimeoutException e) {
				e.printStackTrace();
				throw new ServletException("Failed to shutdown / Timeout : '" + defaultRequestQueue + "' :  " + e.getMessage());
			} finally {
				onLineGlob = null;
			}	
		}
	}

	/**
	 * @see HttpServlet#HttpServlet()
	 */
	public RPCMessageBus() {
		super();
	}

	/**
	 * Get the configuration parameter from annotations, web.xml overrides.
	 * 
	 * @see Servlet#init()
	 */

	public void init() throws ServletException {
		logger = Logger.getLogger(getClass().getName());
		String doLogStr = getServletConfig().getInitParameter(LOG_CONFIG);
		if (doLogStr != null) {
			doLog = Boolean.parseBoolean(doLogStr);
		}
		ServletConfig servletConfig = getServletConfig();
		if (doLog) {
			Enumeration<String> names = servletConfig.getInitParameterNames();
			while (names.hasMoreElements()) {
				String name = names.nextElement();
				logger.log(Level.FINE, String.format("@INIT Config params name:%s, value:%s", name,
						servletConfig.getInitParameter(name)));
			}
		}

		defaultRequestQueue = getServletConfig().getInitParameter(DEFAULT_QUEUE_CONFIG);
		if (defaultRequestQueue == null) {
			throw new ServletException(
					"Missing '" + DEFAULT_QUEUE_CONFIG + "' configuration value, cannot connect without queue name.");
		}
		logger.log(Level.INFO, String.format("@INIT Utilizing defaultRequestQueue : %s ", defaultRequestQueue));

		queueHost = getServletConfig().getInitParameter(QUEUE_HOST_CONFIG);
		if (queueHost == null) {
			throw new ServletException(
					"Missing '" + QUEUE_HOST_CONFIG + "' configuration value, cannot connect without host definition.");
		}
		logger.log(Level.INFO, String.format("@INIT Utilizing queueHost : %s ", queueHost));

		username = getServletConfig().getInitParameter(USERNAME_CONFIG);
		logger.log(Level.INFO, String.format("@INIT Utilizing username : %s ", username));

		password = getServletConfig().getInitParameter(PASSWORD_CONFIG);
		if (password != null) {
			logger.log(Level.INFO, String.format("@INIT Utilizing password, it has been set. (%s)", password));
		} else {
			logger.log(Level.INFO, String.format("@INIT Not intilizing password, it has NOT been set."));
		}
		String timeoutStr = getServletConfig().getInitParameter(TIMEOUT_CONFIG);
		if (timeoutStr != null) {
			try {
				timeout = Integer.parseInt(timeoutStr);
			} catch (NumberFormatException e) {
				logger.log(Level.SEVERE,
						String.format("@INIT Timeout value conversion failed invalid:'%s'.", timeoutStr));
			}
		}
		logger.log(Level.INFO, String.format("@INIT Utilizing timeout : %d", timeout));

		// Work out the dynamic queue mapping
		String contextQueueMapStr = getServletConfig().getInitParameter(PATHQUEUE_MAP_CONFIG);
		if (contextQueueMapStr == null) {
			pathQueueMap = new HashMap<String, String>();
		} else {
			logger.log(Level.INFO, String.format("@INIT contextQueueMapStr:%s", contextQueueMapStr));
			pathQueueMap = new Gson().fromJson(contextQueueMapStr, Map.class);
		}
		logger.log(Level.INFO, String.format("@INIT pathQueueMap:%s", pathQueueMap.toString()));

	}

	/**
	 * @see Servlet#getServletInfo()
	 */
	public String getServletInfo() {
		return String.format("Web access to Streams via MessageBus; Copyright IBM corp. version:%s", "0.1");
	}

	int activeCount = 1;

	boolean composeResponse(HttpServletResponse response, String responseMq) throws IOException {
		return true;
	}

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		Payload payload = new Payload(request, pathQueueMap, defaultRequestQueue, queueHost, username, password);
		logger.log(Level.INFO,
				String.format("corrId:%s * doGet queryStrng:%s", payload.correlationId, request.getQueryString()));
		logger.log(Level.INFO,
				String.format("corrId:%s - requestQueue:%s, queue may have been dynamically derived via configuration.",
						payload.correlationId, payload.getRequestQueueName()));
		payload.asyncContext = request.startAsync();

		payload.rpcServletListener = new RPCAsyncMonitor(payload);
		payload.asyncContext.addListener(payload.rpcServletListener);
		payload.asyncContext.setTimeout(timeout);
		try {
			payload.asyncContext.start(new AsyncRequest(payload, response));
		} catch (TimeoutException e) {
			logger.log(Level.SEVERE, "Timeout @ contextStart " + e.getMessage());		
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void destroy() {
		log("Shutting down connection '" + defaultRequestQueue + "'.");
		System.out.println("shutdown!!!!");
		try {
			busDown();
		} catch (ServletException e) {
			log("Error shuttting down connection '" + defaultRequestQueue + "':" + e.getMessage());
		}
		return;
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse
	 *      response)
	 */
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		doGet(request, response);
	}

}
