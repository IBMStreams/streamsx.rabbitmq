#  J2EE Gateway to RabbitMQRequestProcess() operator

## Overview :
This sample has two compononts: a Streams application and 
J2EE Liberty Servlet that communicate via RabbitMQ.  Streams does the processing, 
Liberty is web interface and RabbitMQ is the arbitrator.   
The compoents can be  scaled independently by adding more Sevlets or more Streams
applications as load changes. 

The directories : 

* RabbitMQRabbitRestServer : A Steams application that executes commands: 
sleep, fill and mirror. Commands and the corresponding results are
communicated via RabbitMQ. 
* MqStreamsProxy : A J2EE Servlet that accepts REST 
requests, transmits them via MQRabbit which 
are processed by the Streams application. The 
processing results are returned following the opposite path. 


## Components 

In order to demo you need the following. My development 
consists of a Mac running OSX and QSE4.2 VM running on the
same machine. 

* RabbitMQ installed configured and running. This is running 
on the Mac. 
* LibertySever Installed and running. This is running on the Mac, 
I'm using the Bluemix Cloud  since include and IDE as well. 
* Optional : Maven installed, on the VM hosting system, in my case that is 
on the Mac. Streams QSE has maven installed, you can build within the QSE and copy 
the resulting war to the with Liberty for deployment.  

## Configuration 
1) In the scenario presented here, RabbitMQ is on the
VMHost (mac) moving messages between the mac and the VM using the default 
RabbitMQ username password (guest/guest). 
~~~
By default, the guest user is prohibited from connecting to the broker remotely; it can 
only connect over a loopback interface (i.e. localhost). 
~~~
To remedy the situation refer to  : https://www.rabbitmq.com/access-control.html. 
 

2) You will need the host computer name or IP when submitting the Streams 
application, in my case it charon.local.  


# Bringing up the components. 

* Bring up RabbitMQ

* Import the Streams application, build and run it. It can run 
in standalone or distribured mode.

* Build the Server using maven 
~~~
> cd ... MqStreamsProxy
> mvn clean
> mvn war:war
~~~

Deploy the resulting application using
   ... MqStreamProxy/target/MqStreamProxy-1.0.war
   
# Demo  

Rest requests to the application can be made with curl from 
the command line. The request has the following format:

* fill : number of 'A' to return. 
* sleep : number of seconds to wait, simulates computation. 
* mirror : reflect back request. 

## Request 
~~~
curl "http://localhost:9080/MqStreamsProxy/MqStreamsProxy?fill=5&amp;sleep=5&amp;mirror=0"
~~~

## Response
The resut request returns after 5 seconds, note that the 5 'A's of fill. 
~~~
{"sequenceNumber":"44","request":"fill=5&amp;sleep=1&amp;mirror=0","method":"GET","timeString":"","contextPath":"/MqStreamsProxy","block":"1","fill":"AAAAA","pathInfo":"/MqStreamsProxy"}
~~~

