package il.technion.ewolf.kbr.openkad;

import il.technion.ewolf.kbr.Key;
import il.technion.ewolf.kbr.KeyFactory;
import il.technion.ewolf.kbr.KeybasedRouting;
import il.technion.ewolf.kbr.MessageHandler;
import il.technion.ewolf.kbr.Node;
import il.technion.ewolf.kbr.concurrent.CompletionHandler;
import il.technion.ewolf.kbr.concurrent.FutureTransformer;
import il.technion.ewolf.kbr.openkad.handlers.FindNodeHandler;
import il.technion.ewolf.kbr.openkad.handlers.PingHandler;
import il.technion.ewolf.kbr.openkad.handlers.StoreHandler;
import il.technion.ewolf.kbr.openkad.msg.ContentMessage;
import il.technion.ewolf.kbr.openkad.msg.ContentRequest;
import il.technion.ewolf.kbr.openkad.msg.ContentResponse;
import il.technion.ewolf.kbr.openkad.msg.KadMessage;
import il.technion.ewolf.kbr.openkad.net.KadServer;
import il.technion.ewolf.kbr.openkad.net.MessageDispatcher;
import il.technion.ewolf.kbr.openkad.net.filter.IdMessageFilter;
import il.technion.ewolf.kbr.openkad.net.filter.TagMessageFilter;
import il.technion.ewolf.kbr.openkad.net.filter.TypeMessageFilter;
import il.technion.ewolf.kbr.openkad.op.FindValueOperation;
import il.technion.ewolf.kbr.openkad.op.JoinOperation;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;

public class KadNet implements KeybasedRouting {

	// dependencies
	private final Provider<MessageDispatcher<Object>> msgDispatcherProvider;
	private final Provider<JoinOperation> joinOperationProvider;
	private final Provider<ContentRequest> contentRequestProvider;
	private final Provider<ContentMessage> contentMessageProvider;
	private final Provider<IncomingContentHandler<Object>> incomingContentHandlerProvider;
	private final Provider<FindValueOperation> findValueOperationProvider;
	private final Provider<FindNodeHandler> findNodeHandlerProvider;
	private final Provider<PingHandler> pingHandler;
	private final Provider<StoreHandler> storeHandlerProvider;
	
	private final Node localNode;
	private final KadServer kadServer;
	private final KBuckets kBuckets;
	private final KeyFactory keyFactory;
	private final ExecutorService clientExecutor;
	private final int bucketSize;
	
	//testing
	private final List<Integer> findNodeHopsHistogram;
	
	
	@Inject
	KadNet(
			Provider<MessageDispatcher<Object>> msgDispatcherProvider,
			Provider<JoinOperation> joinOperationProvider,
			Provider<ContentRequest> contentRequestProvider,
			Provider<ContentMessage> contentMessageProvider,
			Provider<IncomingContentHandler<Object>> incomingContentHandlerProvider,
			Provider<FindValueOperation> findValueOperationProvider,
			Provider<FindNodeHandler> findNodeHandlerProvider,
			Provider<PingHandler> pingHandler,
			Provider<StoreHandler> storeHandlerProvider,
			
			@Named("openkad.local.node") Node localNode,
			KadServer kadServer,
			KBuckets kBuckets,
			KeyFactory keyFactory,
			@Named("openkad.executors.client") ExecutorService clientExecutor,
			@Named("openkad.bucket.kbuckets.maxsize") int bucketSize,
			
			//testing
			@Named("openkad.testing.findNodeHopsHistogram") List<Integer> findNodeHopsHistogram) {
		
		
		this.msgDispatcherProvider = msgDispatcherProvider;
		this.joinOperationProvider = joinOperationProvider;
		this.contentRequestProvider = contentRequestProvider;
		this.contentMessageProvider = contentMessageProvider;
		this.incomingContentHandlerProvider = incomingContentHandlerProvider;
		this.findValueOperationProvider = findValueOperationProvider;
		this.findNodeHandlerProvider = findNodeHandlerProvider;
		this.pingHandler = pingHandler;
		this.storeHandlerProvider = storeHandlerProvider;
		
		this.localNode = localNode;
		this.kadServer = kadServer;
		this.kBuckets = kBuckets;
		this.keyFactory = keyFactory;
		this.clientExecutor = clientExecutor;
		this.bucketSize = bucketSize;
		
		//testing
		this.findNodeHopsHistogram = findNodeHopsHistogram;
	}
	
	
	@Override
	public void create() throws IOException {
		// bind communicator and register all handlers
		kadServer.bind();
		pingHandler.get().register();
		findNodeHandlerProvider.get().register();
		storeHandlerProvider.get().register();
		
		kBuckets.registerIncomingMessageHandler();
		new Thread(kadServer).start();
	}

	@Override
	public void join(Collection<URI> bootstraps) throws Exception {
		joinOperationProvider.get()
			.addBootstrap(bootstraps)
			.call();
	}

	@Override
	public List<Node> findNode(Key k, int n) throws Exception {
		
		FindValueOperation op = findValueOperationProvider.get()
			.setMaxNodes(n)
			.setKey(k);
		
		
		List<Node> result = op.call();
		findNodeHopsHistogram.add(op.getNrQueried());
		
		List<Node> $ = new ArrayList<Node>(result);
		
		if ($.size() > n)
			$.subList(n, $.size()).clear();
		
		return result;
	}
	
	@Override
	public List<Node> findNode(Key k) throws Exception {
		return findNode(k, bucketSize);
	}

	@Override
	public KeyFactory getKeyFactory() {
		return keyFactory;
	}

	@Override
	public List<Node> getNeighbours() {
		return kBuckets.getAllNodes();
	}
	
	@Override
	public Node getLocalNode() {
		return localNode;
	}
	
	@Override
	public String toString() {
		return localNode.toString()+"\n"+kBuckets.toString();
	}

	@Override
	public void register(String tag, MessageHandler handler) {
		msgDispatcherProvider.get()
			.addFilter(new TagMessageFilter(tag))
			.setConsumable(false)
			.setCallback(null, incomingContentHandlerProvider.get()
				.setHandler(handler)
				.setTag(tag))
			.register();
	}

	@Override
	public void sendMessage(Node to, String tag, byte[] msg) throws IOException {
		kadServer.send(to, contentMessageProvider.get()
			.setTag(tag)
			.setContent(msg));
	}

	@Override
	public Future<byte[]> sendRequest(Node to, String tag, byte[] msg) throws Exception {
		ContentRequest contentRequest = contentRequestProvider.get()
			.setTag(tag)
			.setContent(msg);
		
		Future<KadMessage> futureSend = msgDispatcherProvider.get()
			.setConsumable(true)
			.addFilter(new TypeMessageFilter(ContentResponse.class))
			.addFilter(new IdMessageFilter(contentRequest.getId()))
			.futureSend(to, contentRequest);
		
		return new FutureTransformer<KadMessage, byte[]>(futureSend) {
			@Override
			protected byte[] transform(KadMessage msg) throws Throwable {
				return ((ContentResponse)msg).getContent();
			}
		};
	}
	
	@Override
	public <A> void sendRequest(Node to, String tag, byte[] msg, final A attachment, final CompletionHandler<byte[], A> handler) {
		ContentRequest contentRequest = contentRequestProvider.get()
			.setTag(tag)
			.setContent(msg);
			
		msgDispatcherProvider.get()
			.setConsumable(true)
			.addFilter(new TypeMessageFilter(ContentResponse.class))
			.addFilter(new IdMessageFilter(contentRequest.getId()))
			.setCallback(null, new CompletionHandler<KadMessage, Object>() {
				@Override
				public void completed(KadMessage msg, Object nothing) {
					final ContentResponse contentResponse = (ContentResponse)msg;
					clientExecutor.execute(new Runnable() {
						@Override
						public void run() {
							handler.completed(contentResponse.getContent(), attachment);
						}
					});
				}
				
				@Override
				public void failed(Throwable exc, Object nothing) {
					handler.failed(exc, attachment);
				}
			})
			.send(to, contentRequest);
	}

}