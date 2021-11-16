package tech.ivoice.javax.sip;

import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;

import javax.sip.Transaction;
import javax.sip.TransactionState;
import javax.sip.message.Request;
import java.util.Optional;

/**
 * @see Transaction
 */
public interface SipTransaction {
    boolean isServerTransaction();

    TransactionState getState();

    String getBranchId();

    Request getRequest();

    void addResponse(SIPResponse response);

    Optional<SIPResponse> getLastResponse();

    SIPRequest getOriginalRequest();
}
