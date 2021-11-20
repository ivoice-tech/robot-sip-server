package tech.ivoice.sip.examples.serverwaitingcall;

import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import io.vertx.mutiny.core.Vertx;
import tech.ivoice.sip.vertx.AbstractSipUserAgent;
import tech.ivoice.sip.vertx.SipVerticleConfig;

/**
 * Run it and call from softphone: "uas@127.0.0.1:5080"
 */
public class Uas extends AbstractSipUserAgent<Void> {
    public static void main(String[] args) {
        var vertx = Vertx.vertx();
        var config = new SipVerticleConfig("127.0.0.1", 5080, "udp");
        vertx.deployVerticleAndAwait(new Uas(config));
    }

    public Uas(SipVerticleConfig config) {
        super(config);
    }

    @Override
    protected void onInvite(SIPRequest invite) {
        String callId = invite.getCallId().getCallId();
        SIPResponse ok = createSuccessResponse(callId);
        sendResponse(ok);
    }

    @Override
    protected void onBye(SIPRequest bye) {
        String callId = bye.getCallId().getCallId();
        SIPResponse ok = createSuccessResponse(callId);
        sendResponse(ok);
    }
}
