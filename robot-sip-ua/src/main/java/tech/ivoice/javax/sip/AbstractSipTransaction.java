package tech.ivoice.javax.sip;

import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;

import javax.sip.TransactionState;
import javax.sip.message.Request;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Simplified version of SIPTransactionImpl
 *
 * @see gov.nist.javax.sip.stack.SIPTransactionImpl
 */
abstract class AbstractSipTransaction implements SipTransaction {
    private final TransactionState state;
    private final String branchId;
    private final SIPRequest request;
    protected final List<SIPResponse> responses = new ArrayList<>();

    public AbstractSipTransaction(TransactionState state, String branchId, SIPRequest request) {
        this.state = state;
        this.branchId = branchId;
        this.request = request;
    }

    @Override
    public TransactionState getState() {
        return state;
    }

    @Override
    public String getBranchId() {
        return branchId;
    }

    @Override
    public Request getRequest() {
        return request;
    }

    @Override
    public void addResponse(SIPResponse response) {
        responses.add(response);
    }

    @Override
    public Optional<SIPResponse> getLastResponse() {
        if (responses.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(responses.get(responses.size() - 1));
    }

    @Override
    public SIPRequest getOriginalRequest() {
        return request;
    }
}
