## Update ##
Since Google code no longer support download, the latest release of Openkad can be found here https://drive.google.com/folderview?id=0B__tBG9ZcSc0VnE4b1U2X1BtYWs&usp=sharing

## Key-based routing ##
Key-based routing (KBR) is a lookup method used in conjunction with distributed hash tables (DHTs) and certain other overlay networks. While DHTs provide a method to find a host responsible for a certain piece of data, KBR provides a method to find the closest host for that data, according to some defined metric. This may not necessarily be defined as physical distance, but rather the number of network hops.

## Distributed Hash Table ##
A distributed hash table (DHT) is a class of a decentralized distributed system that provides a lookup service similar to a hash table; (key, value) pairs are stored in a DHT, and any participating node can efficiently retrieve the value associated with a given key. Responsibility for maintaining the mapping from keys to values is distributed among the nodes, in such a way that a change in the set of participants causes a minimal amount of disruption. This allows a DHT to scale to extremely large numbers of nodes and to handle continual node arrivals, departures, and failures.

## Kademlia ##
Kademlia is a distributed hash table for decentralized peer-to-peer computer networks designed by Petar Maymounkov and David Mazières in 2002. It specifies the structure of the network and the exchange of information through node lookups. Kademlia nodes communicate among themselves using UDP. A virtual or overlay network is formed by the participant nodes. Each node is identified by a number or node ID. The node ID serves not only as identification, but the Kademlia algorithm uses the node ID to locate values (usually file hashes or keywords). In fact, the node ID provides a direct map to file hashes and that node stores information on where to obtain the file or resource.

for more information read http://www.springerlink.com/content/2ekx2a76ptwd24qt/

## Keybased Routing Usage example ##
```
// set kademlia udp port and protocol
Properties props = new Properties();
Injector injector1 = Guice.createInjector(new KadNetModule()
    .setProperty("openkad.net.udp.port", "5555"));

Injector injector2 = Guice.createInjector(new KadNetModule()
    .setProperty("openkad.net.udp.port", "5556"));

// create
KeybasedRouting kbr1 = injector1.getInstance(KeybasedRouting.class);
KeybasedRouting kbr2 = injector2.getInstance(KeybasedRouting.class);

// start listening on local port
kbr.create();

// join the network
// format of the uri: [protocol]://[address:port]/
kbr1.join(new URI("openkad.udp://1.2.3.4:5556/"));


KeyFactory keyFactory = kbr.getKeyFactory();

Key k1 = keyFactory.create("any arbitrary data");

// get closest nodes to key k1 
List<Node> nodes = kbr1.findNodes(k1);

// register to receive messages
kbr1.register("some tag", new MessageHandler() {

    void onIncomingMessage(Node from, String tag, Serializable content) {
        System.out.println("I got a message !");
    }
    Serializable onIncomingRequest(Node from, String tag, Serializable content) {
        System.out.println("I got a request !");
        return "Hello";
    }
});

// send message to all nodes
for (Node n : nodes) {
    kbr1.sendMessage(n, "some tag", "Hi");
}

// send request to the first node
Future<Serializable> futureResponse = kbr1.sendRequest(nodes.get(0), "some tag", "Hi");

// do other stuff

// block until request is recved (or timed out)
Serializable response = futureResponse.get();

// should print Hello
System.out.println(response);
```

## E-Wolf Project ##
  * [openkad](http://code.google.com/p/openkad/)
  * [shades](http://code.google.com/p/shades/)
  * [dht](http://code.google.com/p/dht/)
  * [chunkeeper](http://code.google.com/p/chunkeeper/)
  * [social-fs](http://code.google.com/p/social-fs/)
  * [ewolf](http://code.google.com/p/ewolf/)
  * [postman](https://code.google.com/p/postman-pubsub/)

## Our publications ##
