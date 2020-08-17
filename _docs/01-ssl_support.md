---
title: "SSL Support"
permalink: /docs/user/sslsupport/
excerpt: "How to use this toolkit."
last_modified_at: 2020-08-17T12:00:00-04:00
redirect_from:
   - /theme-setup/
sidebar:
   nav: "userdocs"
---
{% include toc %}
{%include editme %}

Securing connections via SSL is a requirement in many application solutions where sensitive data is being transmitted across the network. In this article, I will discuss the new functionality added to the RabbitMQ operators that allow applications to create secured connections to RabbitMQ.

In order to support this, the operators in the RabbitMQ toolkit have been updated to include a new set of parameters that allow users to set SSL-specific configurations.

This functionality is available as of v1.1.0 of the RabbitMQ toolkit. The toolkit can be downloaded from the [RabbitMQ Releases](https://github.com/IBMStreams/streamsx.rabbitmq/releases) page.


## Configuring the RabbitMQ Server for SSL

Your RabbitMQ server must first be configured to use SSL before you can use the SSL support in the RabbitMQ operators. How to configure the RabbitMQ server for SSL is outside the scope of this article. However, the RabbitMQ documentation contains a very succinct guide on how to accomplish this.

That information can be found here: [RabbitMQ - TLS Support](https://www.rabbitmq.com/ssl.html)


## Enabling SSL connections

At a minimum, the following parameters need to be set for the operators in the toolkit to enable SSL:

* `useSSL`: whether or not to enable SSL.
* `keyStorePath`: specifies the path to the keystore file
* `keyStorePassword`: password to unlock the key store.
* `trustStorePath`: path to trust store file.

The `useSSL` parameter is required to enable a SSL connection, and must be set to true. The remaining parameters must be set if the `useSSL` parameter is set to true.


## Sample

The following is a snippet from the RabbitMQSSLSample found on GitHub that demonstrates how to set the SSL parameters on the RabbitMQ source and sink operators.
The complete application can be found in the GitHub repository: https://github.com/IBMStreams/streamsx.rabbitmq/tree/develop/samples/RabbitMQSSLSample


```
composite RabbitMQSSLSample
{
param
expression $hostAndPort : "rabbitHost:5671";
expression $exchangeName : "myPhilosophicalExchange";
expression $routing_key : "What is reality?";
expression $useSSL : true;
expression $keyStorePath : getThisToolkitDir() + "/etc/keystore.jks";
expression $keyStorePassword : "MySecretPassword";
expression $trustStorePath : getThisToolkitDir() + "/etc/truststore.jks";
expression $trustStorePassword : "MySecretPassword";
graph
() as RabbitMQSinkOp = RabbitMQSink(OutputStream)
{
param
hostAndPort : $hostAndPort;
exchangeName : $exchangeName;
useSSL : $useSSL;
keyStorePath : $keyStorePath;
keyStorePassword : $keyStorePassword;
trustStorePath : $trustStorePath;
trustStorePassword : $trustStorePassword;
}

stream RabbitMQStream = RabbitMQSource()
{
param
hostAndPort : $hostAndPort;
exchangeName : $exchangeName;
routingKey : $routing_key;
useSSL : $useSSL;
keyStorePath : $keyStorePath;
keyStorePassword : $keyStorePassword;
trustStorePath : $trustStorePath;
}

//...snipped...
}
```

Other configuration options are available including setting the SSL protocol, or use a different algorithm.


## Different TLS protocol

By default, the operator assumes that the RabbitMQ server has been configured to support TLSv1.2. In the event that the RabbitMQ server does not support this version of TLS, the sslProtocol parameter can be used to set the TLS version that is supported by the server.

## Different Store Types

By default, the operators will set the default keyStore and trustStore types based on what the default store type set by the JVM. For the JVM in IBM Streams 4.3.x, the default store type is JKS. However, it is possible that a different store type is being used. For example, it is not uncommon for a keyStore to be of type PKCS12. If this is the case, then the **keyStoreType** parameter will need to be set to PKCS12.

## Different Algorithm Types

Similar to the store type, the operators are configured to decrypt the keyStore and trustStore using the default algorithm set by the JVM. For the JVM in IBM Streams 4.3.x, the default algorithm is IbmX509. In the majority of cases, this value is acceptable and no further action is required. However, if a different algorithm is required, then the **keyStoreAlgorithm** and **trustStoreAlgorithm** parameters can be used to specify a different algorithm.

## List of all SSL related parameters

The following is a full list of the new SSL-specific parameters that have been included in **RabbitMQSource** and **RabbitMQSink** operators.

Parameter Name	        | SPL Type	| Default	| Description
======================= | ============= | ============= | =============
useSSL	                | boolean	| false	        | Specifies whether an SSL connection should be created. If not specified, the default value is false.
sslProtocol	        | rstring	| TLSv1.2	| Specifies the SSL protocol to use. If not specified, the default value is TLSv1.2.
keyStorePath	        | rstring	| 	        | Specifies the path to the keystore file. This parameter is required if the **useSSL** parameter is set to true.
keyStorePassword	| rstring	| 	        | Specifies the password used to unlock the keystore. This parameter is required if the **useSSL** parameter is set to true.
keyStoreAlgorithm	| rstring	| IbmX509	| Specifies the algorithm that was used to encrypt the keystore. If not specified, the operator will use the JVM's default algorithm (typically IbmX509).
keyStoreType	        | rstring	| JKS	        | Specifies the keystore type. If not specified, the operator will use the JVM's default type (typically JKS).
trustStorePath	        | rstring	| 	        | Specifies the path to the trustStore file. This parameter is required if the **useSSL** parameter is set to true.
trustStorePassword	| rstring	| 	        | Specifies the password used to unlock the trustStore.
trustStoreAlgorithm	| rstring	| IbmX509	| Specifies the algorithm that was used to encrypt the trustStore. If not specified, the operator will use the JVM's default algorithm (typically IbmX509).
trustStoreType	        | rstring	| JKS	        | Specifies the trustStore type. If not specified, the operator will use the JVM's default type (typically JKS).



## Conclusion

In this article I discussed the new parameters added to the RabbitMQ operators in order to support secured connections via SSL. The parameters required in order to enable SSL in the RabbitMQ operators are: **useSSL**, **keyStorePath**, **trustStorePath**, **keyStorePassword**. The remaining parameters are only required if the SSL connection does not support the latest TLS protocol version or the store types are not JKS. Lastly, a code snippet that demonstrates how to configure the operators was provided, along with a link to the complete sample SPL application.



