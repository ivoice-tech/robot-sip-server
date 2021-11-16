package tech.ivoice.sip.vertx;

import gov.nist.javax.sip.address.AddressFactoryImpl;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.ivoice.javax.sip.SipDialog;

import javax.sip.DialogState;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.message.Response;
import java.text.ParseException;

import static org.junit.jupiter.api.Assertions.*;

class AbstractSipUserAgentTest {
    private final AddressFactory addressFactory = new AddressFactoryImpl();
    private SipUserAgentTestImpl sipVerticle;

    @BeforeEach
    void init() {
        SipVerticleConfig config = new SipVerticleConfig("127.0.0.1", 5080, "tcp");
        sipVerticle = new SipUserAgentTestImpl(config, () -> "mockId");
    }

    @Test
    void createInvite() throws ParseException {
        SipURI targetSipUri = addressFactory.createSipURI("Server", "127.0.0.2:5082");
        SIPRequest invite = sipVerticle.createInvite("Client", targetSipUri);

        String expected = "INVITE sip:Server@127.0.0.2:5082 SIP/2.0\n" +
            "Call-ID: mockId\n" +
            "CSeq: 1 INVITE\n" +
            "From: <sip:Client@127.0.0.1:5080>;tag=initiator-mockId\n" +
            "To: <sip:Server@127.0.0.2:5082>\n" +
            "Via: SIP/2.0/TCP 127.0.0.1:5080;branch=z9hG4bK-mockId\n" +
            "Max-Forwards: 70\n" +
            "Contact: <sip:Client@127.0.0.1:5080>\n" +
            "Content-Length: 0";
        assertEquals(expected, invite.toString().trim().replaceAll("\r", ""));
    }

    @Test
    void whenClientSendInviteThenDialogCreated() throws ParseException {
        SipURI targetSipUri = addressFactory.createSipURI("Server", "127.0.0.2:5082");
        SIPRequest invite = sipVerticle.createInvite("Client", targetSipUri);

        sipVerticle.sendRequest(invite);
        SipDialog<Void> dialog = sipVerticle.findDialog(invite.getCallId());

        assertEquals("mockId", dialog.getDialogId());
        assertFalse(dialog.isServer());
        assertNull(dialog.getState());
    }

    @Test
    void whenServerReceiveInviteThenDialogCreated() throws ParseException {
        SipURI targetSipUri = addressFactory.createSipURI("Server", "127.0.0.2:5082");
        SIPRequest invite = sipVerticle.createInvite("Client", targetSipUri);

        sipVerticle.onRequestReceived(invite);
        SipDialog<Void> dialog = sipVerticle.findDialog(invite.getCallId());

        assertEquals("mockId", dialog.getDialogId());
        assertTrue(dialog.isServer());
        assertNull(dialog.getState());
    }

    @Test
    void whenClientReceiveResponseThenDialogStateUpdated() throws ParseException {
        SipURI targetSipUri = addressFactory.createSipURI("Server", "127.0.0.2:5082");
        SIPRequest invite = sipVerticle.createInvite("Client", targetSipUri);

        sipVerticle.sendRequest(invite); // dialog created

        sipVerticle.onResponse(invite.createResponse(Response.TRYING));
        SipDialog<Void> dialog = sipVerticle.findDialog(invite.getCallId());

        assertEquals(DialogState.EARLY, dialog.getState());
    }

    @Test
    void whenServerSendResponseThenDialogStateUpdated() throws ParseException {
        SipURI targetSipUri = addressFactory.createSipURI("Server", "127.0.0.2:5082");
        SIPRequest invite = sipVerticle.createInvite("Client", targetSipUri);

        sipVerticle.onRequestReceived(invite);// server dialog created

        SIPResponse provisionalResponseOnInvite = invite.createResponse(Response.TRYING);
        sipVerticle.sendResponse(provisionalResponseOnInvite);

        SipDialog<Void> dialog = sipVerticle.findDialog(invite.getCallId());
        assertEquals(DialogState.EARLY, dialog.getState());
    }

    @Test
    void whenNewDialogStateTerminatedThenDialogRemoved() throws ParseException {
        SipURI targetSipUri = addressFactory.createSipURI("Server", "127.0.0.2:5082");
        SIPRequest invite = sipVerticle.createInvite("Client", targetSipUri);

        sipVerticle.sendRequest(invite); // dialog created
        sipVerticle.onResponse(invite.createResponse(Response.OK));
        SIPRequest bye = sipVerticle.createBye(invite.getCallId().getCallId());
        sipVerticle.sendRequest(bye);
        sipVerticle.onResponse(bye.createResponse(Response.OK)); // dialog terminated

        assertThrows(IllegalStateException.class, () -> sipVerticle.findDialog(invite.getCallId()));
    }

    // TODO test dialog termination
}
