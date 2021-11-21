package tech.ivoice.sip.vertx;

import gov.nist.javax.sip.address.AddressFactoryImpl;
import gov.nist.javax.sip.header.CallID;
import gov.nist.javax.sip.header.HeaderFactoryImpl;
import gov.nist.javax.sip.header.Via;
import gov.nist.javax.sip.message.MessageFactoryImpl;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import gov.nist.javax.sip.parser.MessageParser;
import gov.nist.javax.sip.parser.StringMsgParser;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.vertx.core.AbstractVerticle;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.mutiny.core.datagram.DatagramSocket;
import tech.ivoice.javax.sip.*;

import javax.sip.DialogState;
import javax.sip.InvalidArgumentException;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.*;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.text.ParseException;
import java.util.*;
import java.util.function.Supplier;

import static tech.ivoice.javax.sip.SipClientTransaction.MAX_FORWARDS;

/**
 * Invariants:
 * 1. UAC directly calls UAS without intermediate proxies
 * 2. No security and registration
 * <p>
 * Responsibilities:
 * 1. SIP messages creation
 * 2. SIP messages transport (sending, receiving)
 * 3. Dialog state handling
 * <p>
 * Behavior:
 * 1. Abstract class does not initiate sending SIP messages, only provide methods for subclasses to create
 * and send message
 *
 * @param <T> application data
 */
public abstract class AbstractSipUserAgent<T> extends AbstractVerticle {
    protected static final Logger log = LoggerFactory.getLogger(AbstractSipUserAgent.class);

    // https://datatracker.ietf.org/doc/html/rfc3261#section-8.1.1.7
    private static final String BRANCH_MAGIC_COOKIE = "z9hG4bK-";

    private final Transport transport;

    private final MessageFactory messageFactory;
    private final MessageParser messageParser;

    private final Supplier<String> idGenerator;
    private final Supplier<String> branchIdGenerator;

    private final Map<String, SipDialog<T>> dialogs = new HashMap<>();

    //region INTERFACE
    protected final SipVerticleConfig config;

    protected final HeaderFactory headerFactory;
    protected final AddressFactory addressFactory;

    protected void setApplicationData(String callId, T data) {
        findDialog(callId).setApplicationData(data);
    }

    protected T getApplicationData(String callId) {
        return findDialog(callId).getApplicationData();
    }

    public SIPRequest createInvite(String fromUser, SipURI requestUri) {
        // INVITE is create outside of Dialog, that's why implemented here, unlike other requests
        // https://datatracker.ietf.org/doc/html/rfc3261#section-13.2.1
        try {
            Address toNameAddress = addressFactory.createAddress(requestUri);
            // tag is null because no dialog is established yet
            ToHeader toHeader = headerFactory.createToHeader(toNameAddress, null);

            SipURI fromSipUri = addressFactory.createSipURI(fromUser, config.getHostPort());
            Address fromNameAddress = addressFactory.createAddress(fromSipUri);
            String tag = "initiator-" + idGenerator.get();
            FromHeader fromHeader = headerFactory.createFromHeader(fromNameAddress, tag);

            ContactHeader contactHeader = headerFactory.createContactHeader(fromNameAddress);

            String branch = branchIdGenerator.get();
            ViaHeader via = headerFactory.createViaHeader(config.getHost(),
                config.getPort(),
                config.getTransport(),
                branch);
            List<ViaHeader> viaHeaders = Collections.singletonList(via);

            CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, Request.INVITE);
            MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(MAX_FORWARDS);

            CallIdHeader callIdHeader = new CallID(idGenerator.get());
//            ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader("application", "sdp");

            //TODO sdp

            SIPRequest request = (SIPRequest) messageFactory.createRequest(requestUri,
                Request.INVITE, callIdHeader, cSeqHeader, fromHeader,
                toHeader, viaHeaders, maxForwards);
            request.setHeader(contactHeader);
            return request;
        } catch (ParseException | InvalidArgumentException e) {
            throw new IllegalArgumentException(e);
        }
    }

    protected final SIPRequest createAck(SIPResponse response) {
        String callId = response.getCallId().getCallId();
        SipDialog<T> dialog = findDialog(callId);
        return dialog.createAck();
    }

    protected SIPRequest createBye(String callId) {
        SipDialog<T> dialog = findDialog(callId);
        return dialog.createRequest(Request.BYE);
    }

    protected SIPRequest createMessage(String callId, String message) {
        SipDialog<T> dialog = findDialog(callId);
        SIPRequest request = dialog.createRequest(Request.MESSAGE);
        try {
            ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader("application", "text");
            request.setContent(message, contentTypeHeader);
            return request;
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    protected final SIPResponse createTrying(String callId) {
        SipDialog<T> dialog = findDialog(callId);
        return dialog.createProvisionalResponse(Response.TRYING);
    }

    protected final SIPResponse createOk(String callId, String sdp) {
        SipDialog<T> dialog = findDialog(callId);
        SIPRequest lastRequest = dialog.getLastRequest();
        if (!lastRequest.getMethod().equals(Request.INVITE)) {
            throw new IllegalStateException("Success response with SDP must be created on INVITE request");
        }

        try {
            ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader("application", "sdp");
            byte[] contents = sdp.getBytes();
            SIPResponse response = dialog.createSuccessResponse(() -> "server-" + idGenerator.get());
            response.setContent(contents, contentTypeHeader);
            return response;
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    protected final SIPResponse createOk(String callId) {
        SipDialog<T> dialog = findDialog(callId);
        return dialog.createSuccessResponse(() -> "server-" + idGenerator.get());
    }

    protected final void sendRequest(SIPRequest request) {
        //noinspection StatementWithEmptyBody
        if (!request.getMethod().equals(Request.MESSAGE)) {
            SipTransaction transaction = new SipClientTransactionImpl(request);
            addDialogTransaction(transaction);
        } else {
            // do not create transaction for MESSAGE
        }
        logSendingRequest(request);
        transport.send(request);
    }

    protected final void sendResponse(SIPResponse response) {
        logSendingResponse(response);
        transport.send(response);
        SipDialog<T> updated = findDialog(response.getCallId()).updateOnResponse(response);
        if (updated.getState().equals(DialogState.TERMINATED)) {
            this.dialogs.remove(updated.getDialogId());
            onDialogTerminated(response);
        }
    }

    protected void onInvite(SIPRequest invite) {
    }

    protected void onBye(SIPRequest bye) {
    }

    protected void onMessage(SIPRequest message) {
    }

    protected void onProvisionalResponse(SIPResponse response) {
    }

    protected void onClientDialogConfirmed(SIPResponse successResponseOnInvite) {
    }

    protected void onServerDialogConfirmed(SIPRequest ack) {
    }

    protected void onDialogTerminated(SIPResponse successResponseOnBye) {
    }
    //endregion

    //region Init

    public AbstractSipUserAgent(SipVerticleConfig config) {
        this(config, () -> UUID.randomUUID().toString());
    }

    /**
     * Initializes jain-sip factories with default implementations
     */
    public AbstractSipUserAgent(SipVerticleConfig config, Supplier<String> idGenerator) {
        this(config,
            new AddressFactoryImpl(),
            new HeaderFactoryImpl(),
            new MessageFactoryImpl(),
            new StringMsgParser(),
            idGenerator);
    }

    public AbstractSipUserAgent(SipVerticleConfig config,
                                AddressFactory addressFactory,
                                HeaderFactory headerFactory,
                                MessageFactory messageFactory,
                                MessageParser messageParser,
                                Supplier<String> idGenerator) {
        this.config = config;
        if (config.getTransport().equals("udp")) {
            this.transport = new UdpTransport();
        } else {
            throw new RuntimeException("tcp transport not implemented yet");
        }

        this.addressFactory = addressFactory;
        this.headerFactory = headerFactory;
        this.messageFactory = messageFactory;
        this.messageParser = messageParser;

        this.idGenerator = idGenerator;
        this.branchIdGenerator = () -> BRANCH_MAGIC_COOKIE + idGenerator.get();
    }

    /**
     * For testing only, to initialize transport with mock
     */
    public AbstractSipUserAgent(SipVerticleConfig config, Transport transport, Supplier<String> idGenerator) {
        this.config = config;
        this.transport = transport;

        this.addressFactory = new AddressFactoryImpl();
        this.headerFactory = new HeaderFactoryImpl();
        this.messageFactory = new MessageFactoryImpl();
        this.messageParser = new StringMsgParser();

        this.idGenerator = idGenerator;
        this.branchIdGenerator = () -> BRANCH_MAGIC_COOKIE + idGenerator.get();
    }

    @Override
    public Uni<Void> asyncStart() {
        return transport.asyncStartListener();
    }

    /**
     * Override for initialization after server started listening for SIP messages
     */
    protected void onServerStartedListening() {
    }
    //endregion

    private void onIncomingMessage(byte[] bytes) {
        try {
            SIPMessage message = messageParser.parseSIPMessage(bytes,
                true,
                false,
                (ex, msg, headerClass, headerText, messageText) -> { // ParseExceptionListener
                    throw new IllegalArgumentException(ex);
                });
            if (message instanceof SIPRequest) {
                onRequestReceived((SIPRequest) message);
            } else if (message instanceof SIPResponse) {
                onResponse((SIPResponse) message);
            } else {
                throw new IllegalStateException();
            }
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    final void onRequestReceived(SIPRequest request) {
        logOnRequest(request);
        if (request.getMethod().equals(Request.MESSAGE)) {
            onMessage(request);
            return;
        }
        SipServerTransaction serverTransaction = new SipServerTransactionImpl(request);
        addDialogTransaction(serverTransaction);

        switch (request.getMethod()) {
            case Request.INVITE:
                onInvite(request);
                return;
            case Request.BYE:
                onBye(request);
                return;
            case Request.ACK:
                onServerDialogConfirmed(request);
                return;
            default:
                throw new IllegalArgumentException("Unexpected SIP request method: " + request.getMethod());
        }
    }

    final void onResponse(SIPResponse response) {
        logOnResponse(response);

        SipDialog<T> updatedDialog = findDialog(response.getCallId()).updateOnResponse(response);
        if (updatedDialog.getState().equals(DialogState.CONFIRMED)) {
            onClientDialogConfirmed(response);
        } else if (updatedDialog.getState().equals(DialogState.TERMINATED)) {
            this.dialogs.remove(updatedDialog.getDialogId());
            onDialogTerminated(response);
        }

        int code = response.getStatusCode();
        if (code >= 100 && code < 200) {
            onProvisionalResponse(response);
        }
    }

    //region Dialog state
    private void addDialogTransaction(SipTransaction transaction) {
        if (transaction.getOriginalRequest().getMethod().equals(Request.INVITE)) {
            createDialogOnOriginalTransaction(transaction);
        } else {
            SipDialog<T> dialog = findDialog(transaction.getOriginalRequest().getCallId());
            dialog.addTransaction(transaction);
        }
    }

    private void createDialogOnOriginalTransaction(SipTransaction transaction) {
        String callId = transaction.getOriginalRequest().getCallId().getCallId();
        if (dialogs.containsKey(callId)) {
            throw new IllegalStateException("Dialog already exists on transaction " + transaction);
        }
        SipDialog<T> dialog = new SipDialogImpl<>(transaction, branchIdGenerator, headerFactory, config.getTransport());
        dialogs.put(dialog.getDialogId(), dialog);
    }

    final SipDialog<T> findDialog(String callId) {
        if (dialogs.containsKey(callId)) {
            return dialogs.get(callId);
        } else {
            throw new IllegalStateException("dialog not found, probably terminated; callId = " + callId);
        }
    }

    final SipDialog<T> findDialog(CallIdHeader callId) {
        return findDialog(callId.getCallId());
    }
    //endregion

    //region Logging
    private String getUser(HeaderAddress headerAddress) {
        return ((SipURI) headerAddress.getAddress().getURI()).getUser();
    }

    private void logSendingRequest(SIPRequest request) {
        if (log.isTraceEnabled()) {
            log.trace(getUser(request.getFrom()) + ": sending\n" + request);
        } else if (log.isDebugEnabled()) {
            log.debug(getUser(request.getFrom()) + ": sending " + request.getFirstLine().replaceAll("[\\r\\n]", ""));
        }
    }

    private void logSendingResponse(SIPResponse response) {
        if (log.isTraceEnabled()) {
            log.trace(getUser(response.getTo()) + ": responding\n" + response);
        } else if (log.isDebugEnabled()) {
            log.debug(getUser(response.getTo()) + ": responding " + response.getReasonPhrase() +
                " for Call-ID=" + response.getCallId().getCallId());
        }
    }

    private void logOnRequest(SIPRequest request) {
        if (log.isTraceEnabled()) {
            log.trace(getUser(request.getTo()) + ": got request\n" + request);
        } else if (log.isDebugEnabled()) {
            log.debug(getUser(request.getTo()) + ": got " + request.getMethod() + " " +
                request.getFrom().toString().replaceAll("[\\r\\n]", ""));
        }
    }

    private void logOnResponse(SIPResponse response) {
        if (log.isTraceEnabled()) {
            log.trace(getUser(response.getFrom()) + ": got response: " + response);
        } else if (log.isDebugEnabled()) {
            log.debug(getUser(response.getFrom()) + " got " +
                response.getFirstLine().replaceAll("[\\r\\n]", "") + " FROM " +
                response.getTo().getAddress().toString().replaceAll("[\\r\\n]", ""));
        }
    }
    //endregion

    public interface Transport {
        Uni<Void> asyncStartListener();

        void send(SIPRequest request);

        void send(SIPResponse response);
    }

    private final class UdpTransport implements Transport {
        private DatagramSocket socket;

        @Override
        public Uni<Void> asyncStartListener() {
            return vertx.createDatagramSocket(new DatagramSocketOptions())
                .listen(config.getPort(), config.getHost())
                .onItem().invoke(socket -> {
                    if (log.isTraceEnabled()) {
                        log.trace("Listening " + config.getHostPort());
                    }
                })
                .onItem().invoke(socket -> this.socket = socket)
                .onItem().invoke(AbstractSipUserAgent.this::onServerStartedListening)
                .onItem().invoke(socket -> socket.handler(packet -> onIncomingMessage(packet.data().getBytes())))
                .onFailure().invoke(throwable -> log.error(throwable.getMessage()))
                .replaceWithVoid();
        }

        @Override
        public void send(SIPRequest request) {
            SipURI target = (SipURI) request.getTo().getAddress().getURI();
            send(request, target.getPort(), target.getHost());
        }

        @Override
        public void send(SIPResponse response) {
            Via requestSentVia = response.getTopmostVia();
            send(response, requestSentVia.getPort(), requestSentVia.getHost());
        }

        private void send(SIPMessage message, int port, String host) {
            this.socket.send(message.encode(), port, host)
                .subscribe()
                .with(success -> {
                });
        }
    }

    // TODO tcp transport
}
