package tech.ivoice.javax.sip;

import gov.nist.javax.sip.address.AddressFactoryImpl;
import gov.nist.javax.sip.header.HeaderFactoryImpl;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.ivoice.sip.vertx.SipUserAgentTestImpl;
import tech.ivoice.sip.vertx.SipVerticleConfig;

import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.HeaderFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.text.ParseException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SipDialogImplTest {
    private final AddressFactory addressFactory = new AddressFactoryImpl();
    private final HeaderFactory headerFactory = new HeaderFactoryImpl();
    private SipUserAgentTestImpl sipVerticle;

    @BeforeEach
    void init() {
        SipVerticleConfig config = new SipVerticleConfig("127.0.0.1", 5080, "udp");
        sipVerticle = new SipUserAgentTestImpl(config, () -> "mockId");
    }

    @Test
    void localPartyForClientDialog() throws ParseException {
        SipURI target = addressFactory.createSipURI("Server", "127.0.0.2:5082");
        SIPRequest invite = sipVerticle.createInvite("Client", target);
        SipClientTransaction clientTransaction = new SipClientTransactionImpl(invite);
        SipDialog<Void> dialog = new SipDialogImpl<>(clientTransaction, () -> "mockId", headerFactory, "tcp");

        assertEquals(addressFactory.createAddress("sip:Client@127.0.0.1:5080"), dialog.getLocalParty());
    }

    @Test
    void remotePartyForClientDialog() throws ParseException {
        SipURI target = addressFactory.createSipURI("Server", "127.0.0.2:5082");
        SIPRequest invite = sipVerticle.createInvite("Client", target);
        SipClientTransaction clientTransaction = new SipClientTransactionImpl(invite);
        SipDialog<Void> dialog = new SipDialogImpl<>(clientTransaction, () -> "mockId", headerFactory, "tcp");

        assertEquals(addressFactory.createAddress("sip:Server@127.0.0.2:5082"), dialog.getRemoteParty());
    }

    @Test
    void localPartyForServerDialog() throws ParseException {
        SipURI target = addressFactory.createSipURI("Server", "127.0.0.2:5082");
        SIPRequest invite = sipVerticle.createInvite("Client", target);
        SipServerTransaction serverTransaction = new SipServerTransactionImpl(invite);
        SipDialog<Void> dialog = new SipDialogImpl<>(serverTransaction, () -> "mockId", headerFactory, "tcp");

        assertEquals(addressFactory.createAddress("sip:Server@127.0.0.2:5082"), dialog.getLocalParty());
    }

    @Test
    void remotePartyForServerDialog() throws ParseException {
        SipURI target = addressFactory.createSipURI("Server", "127.0.0.2:5082");
        SIPRequest invite = sipVerticle.createInvite("Client", target);
        SipServerTransaction serverTransaction = new SipServerTransactionImpl(invite);
        SipDialog<Void> dialog = new SipDialogImpl<>(serverTransaction, () -> "mockId", headerFactory, "tcp");

        assertEquals(addressFactory.createAddress("sip:Client@127.0.0.1:5080"), dialog.getRemoteParty());
    }

    @Test
    void createProvisionalResponse() throws ParseException {
        SipURI target = addressFactory.createSipURI("Server", "127.0.0.2:5082");
        SIPRequest invite = sipVerticle.createInvite("Client", target);
        SipServerTransaction tx = new SipServerTransactionImpl(invite);
        SipDialog<Void> dialog = new SipDialogImpl<>(tx, () -> "mockId", headerFactory, "tcp");

        SIPResponse response = dialog.createProvisionalResponse(Response.TRYING);
        String expected = "SIP/2.0 100 Trying\n" +
            "CSeq: 1 INVITE\n" +
            "Call-ID: mockId\n" +
            "From: <sip:Client@127.0.0.1:5080>;tag=initiator-mockId\n" +
            "To: <sip:Server@127.0.0.2:5082>\n" +
            "Via: SIP/2.0/UDP 127.0.0.1:5080;branch=z9hG4bK-mockId\n" +
            "Content-Length: 0";
        assertEquals(expected, response.encode().trim().replaceAll("\r", ""));
    }

    @Test
    void createProvisionalResponse_whenClientDialogThenException() throws ParseException {
        SipURI target = addressFactory.createSipURI("Server", "127.0.0.2:5082");
        SIPRequest invite = sipVerticle.createInvite("Client", target);
        SipClientTransaction clientTransaction = new SipClientTransactionImpl(invite);
        SipDialog<Void> dialog = new SipDialogImpl<>(clientTransaction, () -> "mockId", headerFactory, "tcp");

        assertThrows(IllegalStateException.class, () -> dialog.createProvisionalResponse(Response.TRYING));
    }

    @Test
    void createProvisionalResponse_whenNonProvisionalStatusCodeThenException() throws ParseException {
        SipURI target = addressFactory.createSipURI("Server", "127.0.0.2:5082");
        SIPRequest invite = sipVerticle.createInvite("Client", target);
        SipServerTransaction tx = new SipServerTransactionImpl(invite);
        SipDialog<Void> dialog = new SipDialogImpl<>(tx, () -> "mockId", headerFactory, "tcp");

        assertThrows(IllegalArgumentException.class, () -> dialog.createProvisionalResponse(Response.OK));
    }

    @Test
    void createSuccessResponseOnInvite() throws ParseException {
        SipURI target = addressFactory.createSipURI("Server", "127.0.0.2:5082");
        SIPRequest invite = sipVerticle.createInvite("Client", target);
        SipServerTransaction tx = new SipServerTransactionImpl(invite);
        SipDialog<Void> dialog = new SipDialogImpl<>(tx, () -> "mockId", headerFactory, "tcp");

        SIPResponse response = dialog.createSuccessResponse(() -> "newToTag");
        String expected = "SIP/2.0 200 OK\n" +
            "CSeq: 1 INVITE\n" +
            "Call-ID: mockId\n" +
            "From: <sip:Client@127.0.0.1:5080>;tag=initiator-mockId\n" +
            "To: <sip:Server@127.0.0.2:5082>;tag=newToTag\n" +
            "Via: SIP/2.0/UDP 127.0.0.1:5080;branch=z9hG4bK-mockId\n" +
            "Contact: <sip:Server@127.0.0.2:5082>\n" +
            "Content-Length: 0";
        assertEquals(expected, response.encode().trim().replaceAll("\r", ""));
    }

    @Test
    void createClientRequest() throws ParseException {
        SipURI target = addressFactory.createSipURI("Server", "127.0.0.2:5082");
        SIPRequest invite = sipVerticle.createInvite("Client", target);
        SipClientTransaction clientTransaction = new SipClientTransactionImpl(invite);
        SipDialog<Void> dialog = new SipDialogImpl<>(clientTransaction, () -> "mockId", headerFactory, "tcp");

        // init dialog state
        SIPResponse successFromServer = invite.createResponse(Response.OK);
        successFromServer.setToTag("serverToTag"); // server must setup TO tag responding success to invite
        dialog.updateOnResponse(successFromServer); // state initialized

        SIPRequest bye = dialog.createRequest(Request.BYE);
        String expected = "BYE sip:Server@127.0.0.2:5082 SIP/2.0\n" +
            "Via: SIP/2.0/UDP 127.0.0.1:5080;branch=mockId\n" +
            "CSeq: 2 BYE\n" +
            "From: <sip:Client@127.0.0.1:5080>;tag=initiator-mockId\n" +
            "To: <sip:Server@127.0.0.2:5082>;tag=serverToTag\n" +
            "Call-ID: mockId\n" +
            "Max-Forwards: 70\n" +
            "Content-Length: 0";
        assertEquals(expected, bye.encode().trim().replaceAll("\r", ""));
    }

    @Test
    void createServerRequest() throws ParseException {
        SipURI target = addressFactory.createSipURI("Server", "127.0.0.2:5082");
        SIPRequest invite = sipVerticle.createInvite("Client", target);
        SipServerTransaction serverTransaction = new SipServerTransactionImpl(invite);
        SipDialog<Void> dialog = new SipDialogImpl<>(serverTransaction, () -> "mockId", headerFactory, "tcp");

        // init dialog state
        SIPResponse successFromServer = dialog.createSuccessResponse(() -> "toTagMock");
        dialog.updateOnResponse(successFromServer); // state initialized

        SIPRequest bye = dialog.createRequest(Request.BYE);
        System.out.println(bye);
        String expected = "BYE sip:Client@127.0.0.1:5080 SIP/2.0\n" +
            "Via: SIP/2.0/TCP 127.0.0.2:5082;branch=mockId\n" +
            "CSeq: 2 BYE\n" +
            "From: <sip:Server@127.0.0.2:5082>;tag=toTagMock\n" +
            "To: <sip:Client@127.0.0.1:5080>;tag=initiator-mockId\n" +
            "Call-ID: mockId\n" +
            "Max-Forwards: 70\n" +
            "Content-Length: 0";
        assertEquals(expected, bye.encode().trim().replaceAll("\r", ""));
    }

    @Test
    void createAck() throws ParseException {
        SipURI target = addressFactory.createSipURI("Server", "127.0.0.2:5082");
        SIPRequest invite = sipVerticle.createInvite("Client", target);
        SipClientTransaction clientTransaction = new SipClientTransactionImpl(invite);
        SipDialog<Void> dialog = new SipDialogImpl<>(clientTransaction, () -> "ackBranchMockId", headerFactory, "tcp");
        SIPResponse successResponse = invite.createResponse(Response.OK);
        dialog.updateOnResponse(successResponse);
        SIPRequest ack = dialog.createAck();
        System.out.println(ack);

        assertEquals(invite.getRequestURI(), ack.getRequestURI());
        assertEquals(invite.getFrom(), ack.getFrom());
        assertEquals(invite.getTo(), ack.getTo());
        assertEquals("ackBranchMockId", ack.getTopmostVia().getBranch());
        assertEquals(invite.getCSeq().getSeqNumber(), ack.getCSeq().getSeqNumber());
        assertEquals("ACK", ack.getCSeq().getMethod());
    }

    @Test
    void createAck_whenNoSuccessResponseThenException() throws ParseException {
        SipURI target = addressFactory.createSipURI("Server", "127.0.0.2:5082");
        SIPRequest invite = sipVerticle.createInvite("Client", target);
        SipClientTransaction clientTransaction = new SipClientTransactionImpl(invite);
        SipDialog<Void> dialog = new SipDialogImpl<>(clientTransaction, () -> "testAckBranchId", headerFactory, "tcp");
        assertThrows(IllegalStateException.class, dialog::createAck);
    }
}
