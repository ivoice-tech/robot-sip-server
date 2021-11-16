package tech.ivoice.javax.sip;

import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;

import javax.sip.Dialog;
import javax.sip.DialogState;
import javax.sip.address.Address;
import javax.sip.header.CallIdHeader;
import java.util.function.Supplier;

/**
 * Simplified version of SIPDialog with only minimal needed features, see invariants.
 *
 * @param <T> application data class
 * @see Dialog
 * @see gov.nist.javax.sip.stack.SIPDialog
 */
public interface SipDialog<T> {
    SIPRequest createRequest(String method);

    String getDialogId();

    boolean isServer();

    /**
     * @param response sent or received response
     * @return updated dialog
     */
    SipDialog<T> updateOnResponse(SIPResponse response);

    void addTransaction(SipTransaction transaction);

    SIPRequest getLastRequest();

    SIPResponse createProvisionalResponse(int statusCode);

    /**
     * @param uasToTagSupplier supplies tag to create UAS success response on INVITE
     */
    SIPResponse createSuccessResponse(Supplier<String> uasToTagSupplier);

    /**
     * Creates ACK request on last success response
     *
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc3261#section-13.2.2.4">rfc3261#section-13.2.2.4</a>
     */
    SIPRequest createAck();

    Address getRemoteParty();

    Address getLocalParty();

    CallIdHeader getCallId();

    DialogState getState();

    void setApplicationData(T applicationData);

    T getApplicationData();
}
