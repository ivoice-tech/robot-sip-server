package tech.ivoice.sip.examples.simplecallsetup;

import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import io.smallrye.mutiny.Uni;
import tech.ivoice.sip.vertx.AbstractSipUserAgent;
import tech.ivoice.sip.vertx.SipVerticleConfig;

import java.time.Duration;

/**
 * The shootist will send up an invite to the shootme and the victim (shootme) will respond
 * like a UAC should (or it might shoot first).
 */
public class Shootme extends AbstractSipUserAgent<Void> {
    private final String user = "LittleGuy";
    private final boolean hasQuickReflexes;

    /**
     * @param hasQuickReflexes will shoot first (send BYE before Shootist sends BYE)
     */
    public Shootme(SipVerticleConfig config, boolean hasQuickReflexes) {
        super(config);
        this.hasQuickReflexes = hasQuickReflexes;
    }

    @Override
    public void onInvite(SIPRequest invite) {
        String callId = invite.getCallId().getCallId();

        SIPResponse provisionalResponse = createTryingResponse(callId);
        sendResponse(provisionalResponse);

        // send delayed OK responses
        log.info(user + ": accepting invitation to duel with delay...");
        Uni.createFrom().voidItem()
            .onItem().delayIt().by(Duration.ofSeconds(1))
            .subscribe().with(itsTime -> {
                SIPResponse successResponse = createSuccessResponse(callId);
                sendResponse(successResponse);
            });
    }

    @Override
    protected void onServerDialogConfirmed(SIPRequest ack) {
        if (hasQuickReflexes) {
            log.info(user + ": good reaction, preparing to shoot first.");
            // send bye first, before shot
            Uni.createFrom().voidItem()
                .onItem().delayIt().by(Duration.ofMillis(500))
                .subscribe().with(itsTime -> {
                    SIPRequest request = createBye(ack.getCallId().getCallId());
                    log.info(user + ": I'm quick!");
                    sendRequest(request);
                });
        }
    }

    @Override
    public void onBye(SIPRequest bye) {
        log.info(user + ": goodbye the mortal world!");
        SIPResponse response = createSuccessResponse(bye.getCallId().getCallId());
        sendResponse(response);
    }
}
