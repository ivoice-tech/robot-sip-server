package tech.ivoice.sip.examples.simplecallsetup;

import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import io.smallrye.mutiny.Uni;
import tech.ivoice.sip.vertx.AbstractSipUserAgent;
import tech.ivoice.sip.vertx.SipVerticleConfig;

import javax.sip.address.SipURI;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * The shootist will send up an invite to the shootme and the victim (shootme) will respond
 * like a UAC should (or it might even kick up its legs and die).
 * <p>
 * Application state = number of bullets left
 */
public class Shootist extends AbstractSipUserAgent<Void> {
    private final String user = "BigGuy";
    private final SipURI shootmeSipUri;

    public Shootist(SipVerticleConfig config, SipURI shootmeSipUri) {
        super(config);
        this.shootmeSipUri = shootmeSipUri;
    }

    @Override
    protected void onServerStartedListening() {
        sendInviteToShootMe();
    }

    private void sendInviteToShootMe() {
        log.info(user + ": starting duel!");
        SIPRequest request = createInvite(user, shootmeSipUri);
        // Can add custom headers here
        sendRequest(request);
    }

    protected void onProvisionalResponse(SIPResponse provisionalResponse) {
        log.debug(user + ": aware of provisional response " + provisionalResponse.getFirstLine()
            .replaceAll("[\\r\\n]", ""));
    }

    @Override
    protected void onClientDialogConfirmed(SIPResponse response) {
        SIPRequest ack = createAck(response);
        sendRequest(ack);

        log.info(user + ": duel started, aiming...");
        // delay - shootme with quick reflexes can shoot first
        Uni.createFrom().voidItem()
            .onItem().delayIt().by(Duration.of(1, ChronoUnit.SECONDS))
            .map(itsTime -> createBye(response.getCallId().getCallId()))
            .subscribe().with(request -> {
                    log.info(user + ": pew!");
                    sendRequest(request);
                },
                failure -> log.error(user + ": shot failed, " + failure.getMessage()));
    }

    @Override
    protected void onDialogTerminated(SIPResponse successResponseOnBye) {
        log.info(user + ": target destroyed!");
    }

    // if shootme has quick reflexes it will send BYE first
    @Override
    public void onBye(SIPRequest bye) {
        log.info(user + ": damn, you quick! Goodbye the mortal world!");
        SIPResponse response = createSuccessResponse(bye.getCallId().getCallId());
        sendResponse(response);
    }

}
