package tech.ivoice.javax.sip;

import gov.nist.javax.sip.message.SIPRequest;

import javax.sip.TransactionState;
import javax.sip.message.Request;

public class SipClientTransactionImpl extends AbstractSipTransaction implements SipClientTransaction {
    public SipClientTransactionImpl(SIPRequest request) {
        super(initialState(request), request.getTopmostVia().getBranch(), request);
    }

    /**
     * @see TransactionState
     */
    private static TransactionState initialState(SIPRequest request) {
        switch (request.getMethod()) {
            case "INVITE":
                return TransactionState.CALLING;
            case "ACK":
            case "BYE":
                return TransactionState.TRYING;
            default:
                throw new RuntimeException("TODO");
        }
    }

    @Override
    public boolean isServerTransaction() {
        return false;
    }

    // TODO use this method for client tx
    @Override
    public Request createCancel() {
        throw new RuntimeException("TODO");
    }
}
