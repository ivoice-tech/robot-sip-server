package tech.ivoice.sip.examples.humanrobot;

import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import tech.ivoice.sip.vertx.AbstractSipUserAgent;
import tech.ivoice.sip.vertx.SipVerticleConfig;

import javax.sip.address.SipURI;
import java.io.UnsupportedEncodingException;

public class Human extends AbstractSipUserAgent<Void> {
    private final String user = "Human";
    private final SipURI robotSipUri;

    public Human(SipVerticleConfig config, SipURI robotSipUri) {
        super(config);
        this.robotSipUri = robotSipUri;
    }

    @Override
    protected void onServerStartedListening() {
        callRobot();
    }

    private void callRobot() {
        log.info(user + ": calling robot...");
        SIPRequest invite = createInvite(user, robotSipUri);
        sendRequest(invite);
    }

    @Override
    protected void onClientDialogConfirmed(SIPResponse successResponseOnInvite) {
        log.info(user + ": dialog confirmed, sending ACK and waiting robot question...");
        SIPRequest ack = createAck(successResponseOnInvite);
        sendRequest(ack);
    }

    @Override
    protected void onMessage(SIPRequest message) {
        String callId = message.getCallId().getCallId();
        try {
            String question = message.getMessageContent();
            log.info(user + ": robot asking: " + question);
            if (question.contains("name")) {
                SIPRequest answer = createMessage(callId, "John Wayne");
                sendRequest(answer);
            } else if (question.contains("activity")) {
                SIPRequest answer = createMessage(callId, "Shooting");
                sendRequest(answer);
            } else {
                throw new IllegalStateException("unexpected question " + question);
            }
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    protected void onBye(SIPRequest bye) {
        log.info(user + ": robot finished, answering ok.");
        SIPResponse ok = createSuccessResponse(bye.getCallId().getCallId());
        sendResponse(ok);
    }

    @Override
    protected void onDialogTerminated(SIPResponse successResponseOnBye) {
        log.info(user + ": dialog terminated.");
    }
}
