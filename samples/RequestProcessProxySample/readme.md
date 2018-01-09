# MqStreamsProxy : MQRabbit/Streams High Throughput WEB Integration

This example illustrates how the the RabbitMQRequestResponse() operator is used in a high though put web site, the web site is envisioned to have be processing micro services or standard gets/post. This will go through the operating contexts, design and installation. 

## Context 
The intent is provide an example  within a significant web shop. A site that needs to to work loads dynamically without impacting the users experience.

The Streams application would be used to respond to real time processing requests. The web site would specialized components to various aspect site processing. 

- Mobile and Browser rendering.
- Caching context
- Security / Validation 

The schematic illustrates Streams processing embedded in larger WEB environment, integrated with function specific components that process the requests. A significant web site is composed of a components that are routed and replicated to handle the current loads, Streams is anaylytics/streaming component of this. 
The users request has been load balanced and scaled to the Streams application. 

![alt text](awsDesign.jpg)

The take away, Streams handles time bound and resources intensive processing. Superior components exist for: load balancing, routing, session management and rendering would be used in such an environment. 

The sample uses an J2EE Server (Jetty) and AMQP  message (RabbitMQ) message broker. The message broker enables more clients (WebServer) and/or requests processors (Streams) to be added independently. 

## Implementation Specifics
The design is based upon the RabbitMQ's [Remote procedure call RPC](https://www.rabbitmq.com/tutorials/tutorial-six-python.html) pattern. Using the terminology of the design the application server is client of the Streams application. A client creates private response queue on start up, requests are sent over a common queue that all clients and servers are connected. The server (Streams application) gets the requests with a responseQueue identifier and processes the request. Responses are returned on the responseQueue the accompanied the request. 

The design allows new clients (application servers) and server (Streams instances) to be added and removed independently. The only well know resource is the queue that all 
requests from the client to server use. 

The J2EE application uses the J2EEv3 asynchronous processing feature which enables multiple web requests to be outstanding at a time. The Streams example application
 is written as a pipeline.  

![alt text](streamsFlow.jpg)

This simple implementation's drawback becomes apparent when one of the operator's goes compute bound, all the pipeline's prior pipeline nodes are delayed waiting for the CPU bound request to finish. Instantiating another server application resolve the issues, the message hub will distribute requests to the idle server. If the application server becomes inundated, new instance can be added as well. 

The following diagram is a more focused  view the processing distribution. 

![alt text](ibmView.jpg)

This example is a general case of processing Streams requests on a large web site. Significant websites' are customized beyond the scope of this example. The goal is provide some guidance with the provided code in order to integrates Streams component within your website's environment. 

# MqStreamProxy Sample
The sample consists of J2EE Liberty Servlet communicating with Streams via RabbitMQ.  This is a walk through
of bringing up the sample and running a test(s) Tests are invoked using curl to the servlet, the servet passes
the reqeust to Strams that generatates a response and communictates it back to the originating client. 

The components can be scaled independently by adding/removing Servlets or Streams applications as load fluctuates. 


### Directories : 

* RabbitMQRabbitRestServer : A Steams application that executes commands: sleep, fill and mirror. Commands and the corresponding results are
communicated via RabbitMQ. 
* MqStreamsProxy : A J2EE Servlet that accepts REST requests, transmits them via MQRabbit which are processed by the Streams application. The 
processing results are returned following the opposite path. 




## Components 

* [Streams QSE](https://www.ibm.com/developerworks/downloads/im/streamsquick/index.html) : This is Includes Streams development environment where the sample code can be inspected and modified. Where everything runs.
* [RabbitMQ](https://www.rabbitmq.com/download.html) download site. Must be installed and running, the steps are described below.
* Maven : The demo uses maven to install a jetty server and run the war file.
* [JettyServer]:(https://www.eclipse.org/jetty/) : J2EE server used for demo. 
* Optional - [LibertyServer](https://developer.ibm.com/wasdev/downloads/liberty-profile-using-eclipse/) : Includes the Eclipse development environment where the sample code can be inspected and modified. Used for development. 



* RabbitMQ installed, 



# Bring up the components. 

## Intall RabbitMQ

I installed RabbitMQ using this [link](https://gist.github.com/ravibhure/92e780ecc850cd5ab0ab) 

## Bring up RabbitMQ

To bring up the Rabbit
```
sudo service rabbitmq-server start
```
Verify that the server is up...
```bash
sudo rabbitmqctl status
```
Output will describe the state of the system. 

I use the web interface to monitor RabbitMQ. You must enable the iterface once, use this command:
```bash
sudo rabbitmq-plugins enable rabbitmq_managment
```

Access the web console with [http://localhost:15672/#/]([http://localhost:15672/#/), the default
username/password is guest/guest.


## Bring up Streams application

### Build the Toolkit.

```bash
cd ... streamsx.rabbitmq/comstreamsx.rabbitmq
ant clean
ant
```
### Build and run the Streams application. 

Clean and build the Stream application.
```bash
cd samples/RequestProcsSample/RabbitMQRestServe
make clean
make
```
Run the Streams application in Standalone mode.
```bash
make stand
```

### Bring up Servlet

The provided maven pom file will install Jetty, build and run Servlet in Jetty. 

```
cd ... samples/RequestProcessProxySample/MqStreamsProxy
mvn clean
mvn package
mvn war:war
mvn jetty:run-war
```


# Demo  

Rest requests to the application can be made with curl from 
the command line. The request has the following format:

* fill : number of 'A' to return. 
* sleep : number of seconds to wait, simulates computation. 
* mirror : reflect back request. 


### Request 
```bash
curl "http://localhost:9080/MqStreamsProxy/MqStreamsProxy?fill=5&amp;sleep=5&amp;mirror=0"
```

## Response
The result request returns after 5 seconds, note that the 5 'A's of fill. 

```
{"sequenceNumber":"44","request":"fill=5&amp;sleep=1&amp;mirror=0","method":"GET","timeString":"","contextPath":"/MqStreamsProxy","block":"1","fill":"AAAAA","pathInfo":"/MqStreamsProxy"}
```

## Other
* You can invoke mulitple clients and adjust the parameters.
* You can start muliple Streams applications if you want simultaneous processing. 

# Addendums 

## Servlet Addendum 
The servlet has a number of parameters that you may want change for your enviroment, these values
can be set in the web.xml.

* defaultRequestQueue : common queue that all servers are listening on
* log : enable logging
* timeout : time to timeout a reqequest
* queueHost : host rabbitmq is running on
* username : rabbitmq username
* password : rabbitmq password
* asyn-supported : leave as true
* port : port the servlet listens on


Deploy the resulting application using
   ... MqStreamProxy/target/MqStreamProxy-1.0.war
   
```

## RabbitMQ Addendum

* By default, the guest user is prohibited from connecting to the broker remotely; it can only connect over a loopback interface (i.e. localhost). To remedy the situation refer to  : https://www.rabbitmq.com/access-control.html. 



##