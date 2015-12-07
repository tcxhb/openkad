# Introduction #

In this page, I will explain how to extend openkad by adding different operations and messages to it.


# Handlers #
Every incoming message is handled by the registered handlers.
All handlers must extend the class il.technion.ewolf.kbr.openkad.handlers.AbstractHandler

## Filters ##
In this tutorial we will look at the simplest example: The ping handler.
The job of the ping handler is to reply with a ping response to any incoming ping request.
First, we would like our PingHandler to notified only about PingRequests and no other messages. To do this we implement the getFilters method as such:
```
@Override
protected Collection<MessageFilter> getFilters() {
    return Arrays.asList(new MessageFilter[] {
        new TypeMessageFilter(PingRequest.class)
    });
}
```
In the getFilters method, we specify a list of all filters for our handler. In this case we have only one filter: a TypeMessageFilter initiated with the PingRequest message type. The TypeMessageFilter will filter all messages with types different to the type it was initiated with. In our case, all messages with type different from PingRequest will be filtered out and our handler will not handle them.

## Handle ##
After filtering out all unfit messages, our handler is left with the correct message it is willing to handle. The method completed is invoke every time a message has arrived and all the filters did not filter the message out. In our case, the method will be invoked whenever a PingRequest message has arrived. The completed method 2 arguments are:
  1. The KadMessage that has arrived
  1. An attachment, this will always be null
The following is the implementation of the completed method that send a PingResponse back to the requester:
```
@Override
public void completed(KadMessage msg, Void attachment) {
    PingResponse pingResponse =((PingRequest)msg).generateResponse(localNode);		
    try {
        kadServer.send(msg.getSrc(), pingResponse);
    } catch (IOException e) {
        // failed to send the message, nothing to do
        e.printStackTrace();
    }
}
```
In this method we generate the appropriate PingResponse out of the incoming PingRequest and send it to the back to the source using the kadServer object.

## Adding the new handler ##
By now, we have the new handler class (PingHandler) implemented. All is left is to register this handler and allowing it to handle incoming PingRequest messages.
This is done in several places:
  * In the Guice module
  * In the create method of class il.technion.ewolf.kbr.openkad.KadNet

In the Guice module (il.technion.ewolf.kbr.openkad.KadNetModule) All we need to do is to add the following to the configure method:
```
bind(PingHandler.class);
```
This will allow us to create our PingHandler via Guice which will automatically pass all the dependencies, such as KadServer, to our PingHandler's constructor.


In KadNet class, we need to add our handler to the constructor:
```
KadNet(
...
Provider<PingHandler> pingHandler,
...
) {
...
this.pingHandler = pingHandler;
...
}
```

And finally, register the new handler in its create method:
```
public void create() throws IOException {
    ...
    pingHandler.get().register();
    ...
}
```

That's it !! now, any incoming PingRequest sent to our node will be answered with a PingResponse !

# Messages #
Messages in openkad are small classes containing a few primitives. They are received in remote nodes via the handlers as discussed above and are sent invoking the send method of the class il.technion.ewolf.kbr.openkad.net.KadServer.
All messages must be in the il.technion.ewolf.kbr.openkad.msg package. Messages must NOT contain any polymorphic members as the current json serializer does not support it.

Message are categorized into 3 types:
  * A simple message
  * Requests
  * Responses

Simple message are sent without any context saved. They are sent as soon as the network card sends them and there is no guarantee on their arrival.

A requests is a message that we expect a response for from the destination. Their context is saved, a callback can be provided to handle the response and there is a limit on the maximum number of requests which their responses have not arrived yet.

All messages must extend the class il.technion.ewolf.kbr.openkad.KadMessage. All requests must extend the class il.technion.ewolf.kbr.openkad.KadRequest.
All responses must extend the class il.technion.ewolf.kbr.openkad.KadResponse.

## Sending messages ##
Sending a simple message or a response is very simple. After creating the message or response, use KadServer.send method to send it.

Sending a request is a little more complicated and can be done using the class il.technion.ewolf.kbr.openkad.net.MessageDispatcher.
```

// create the PingRequest using Guice
PingRequest pingRequest = pingRequestProvider.get();

msgDispatcherProvider.get()
    // set the callback to be invoked only once
    // and every time a matching message (according
    // to the filters) is received.
    .setConsumable(true)

    // make sure the response for this request will
    // have the same id. This is done in order to
    // save the context much like the port mechanism
    // in TPC and UDP communication.
    .addFilter(new IdMessageFilter(pingRequest.getId()))

    // only allow handling of a PingResponse
    .addFilter(new TypeMessageFilter(PingResponse.class))

    // set the callback to be invoked when the ping
    // response arrives (or not)
    .setCallback(null, new CompletionHandler<KadMessage, Void>() {

        public void completed(KadMessage msg, Void nothing) {
            // ping response was received !!
            PingResponse res = (PingResponse)msg;
            // do whatever you wish to do with the response
        }
        
        public void failed(Throwable exc, Void nothing) {
            // response was not received
            // handle this error in any way you wish
        }
    })

    // sends the request to someNode. This method
    // can block if you have too many unanswered
    // requests.
    .send(someNode, pingRequest); 
```