package tech.ivoice.javax.sip;

import gov.nist.javax.sip.message.SIPRequest;

import javax.sip.TransactionState;
import javax.sip.message.Request;

public class SipServerTransactionImpl extends AbstractSipTransaction implements SipServerTransaction {
    public SipServerTransactionImpl(SIPRequest request) {
        super(initialState(request), request.getTopmostVia().getBranch(), request);
    }

    /**
     * @see TransactionState
     */
    private static TransactionState initialState(SIPRequest request) {
        switch (request.getMethod()) {
            case Request.INVITE:
                return TransactionState.PROCEEDING;
            case Request.ACK:
                return TransactionState.CONFIRMED;
            case Request.BYE:
                return TransactionState.TRYING;
            default:
                throw new RuntimeException("TODO");
        }
    }

    @Override
    public boolean isServerTransaction() {
        return true;
    }
}
