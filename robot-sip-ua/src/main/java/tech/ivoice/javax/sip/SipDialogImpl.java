package tech.ivoice.javax.sip;

import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.CSeq;
import gov.nist.javax.sip.header.MaxForwards;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;

import javax.sip.DialogState;
import javax.sip.InvalidArgumentException;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.header.*;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static gov.nist.javax.sip.stack.SIPDialog.EARLY_STATE;
import static gov.nist.javax.sip.stack.SIPDialog.TERMINATED_STATE;
import static tech.ivoice.javax.sip.SipClientTransaction.MAX_FORWARDS;

/**
 * Simplified version of:
 *
 * @version WIP - need revision before 0.1 ver. lots of stuff copied from nist SIPDialog and need fixing / removing
 * @see gov.nist.javax.sip.stack.SIPDialog
 */
public class SipDialogImpl<T> implements SipDialog<T> {
    private final List<SipTransaction> transactions = new ArrayList<>();

    // needed to construct messages
    private final Supplier<String> branchIdGen;
    private final HeaderFactory headerFactory;
    private final String transport;

    /*
    state on creation is null.
    For UAS, state is constructed when sending responses: https://datatracker.ietf.org/doc/html/rfc3261#section-12.1.1
    For UAC, state is constructed when receiving responses: https://datatracker.ietf.org/doc/html/rfc3261#section-12.1.2
     */
    private DialogState state = null;
    private T applicationData;

    public SipDialogImpl(SipTransaction tx, Supplier<String> branchIdGen, HeaderFactory hdrFactory, String transport) {
        transactions.add(tx);
        this.branchIdGen = branchIdGen;
        this.headerFactory = Objects.requireNonNull(hdrFactory);
        this.transport = transport;
    }

    @Override
    public String getDialogId() {
        return getCallId().getCallId();
    }

    @Override
    public boolean isServer() {
        return transactions.get(0).isServerTransaction();
    }

    @Override
    public SipDialog<T> updateOnResponse(SIPResponse response) {
        SipTransaction lastTransaction = transactions.get(transactions.size() - 1);
        lastTransaction.addResponse(response);
        int code = response.getStatusCode();
        if (code / 100 == 1) { //1xx - provisional response
            state = DialogState.EARLY;
        } else if (code / 100 == 2) {
            String lastRequestMethod = lastTransaction.getOriginalRequest().getMethod();
            if (lastRequestMethod.equals(Request.INVITE)) {
                state = DialogState.CONFIRMED;
            } else if (lastRequestMethod.equals(Request.BYE)) {
                state = DialogState.TERMINATED;
            } else {
                throw new RuntimeException("TODO");
            }
        } else {
            throw new RuntimeException("TODO");
        }
        return this;
    }

    @Override
    public void addTransaction(SipTransaction transaction) {
        transactions.add(transaction);
    }

    private SIPRequest getInitialInvite() {
        SipTransaction firstTx = transactions.get(0);
        SIPRequest request = firstTx.getOriginalRequest();
        if (!request.getMethod().equals(Request.INVITE)) {
            throw new IllegalStateException("First transaction original request must always be invite, but it was " +
                request);
        }
        return request;
    }

    @Override
    public SIPRequest getLastRequest() {
        return transactions.get(transactions.size() - 1).getOriginalRequest();
    }

    // https://datatracker.ietf.org/doc/html/rfc3261#section-12.2.1.1
    @Override
    public SIPRequest createRequest(String method) {
        try {
            if (method.equals(Request.ACK) || method.equals(Request.PRACK) || method.equals(Request.CANCEL)) {
                throw new IllegalArgumentException(
                    "Invalid method specified for createRequest:" + method);
            }
            if (this.getState() == null
                || (this.getState().getValue() == TERMINATED_STATE && !method
                .equalsIgnoreCase(Request.BYE))
                || (this.isServer()
                && this.getState().getValue() == EARLY_STATE && method
                .equalsIgnoreCase(Request.BYE)))
                throw new IllegalStateException("Dialog  " + getDialogId() +
                    " not yet established or terminated " + this.getState());

            CSeq cseq = new CSeq();
            cseq.setMethod(method);
            cseq.setSeqNumber(findLastRequestCseq() + 1);

            FromHeader from;
            ToHeader to;
            ViaHeader via;
            SIPResponse lastResponseOnOriginalInvite = transactions.get(0).getLastResponse().orElseThrow();
            if (isServer()) {
                from = headerFactory.createFromHeader(getLocalParty(), lastResponseOnOriginalInvite.getToTag());
                to = headerFactory.createToHeader(getRemoteParty(), lastResponseOnOriginalInvite.getFromTag());
                SipURI sipUri = (SipURI) getLocalParty().getURI();
                via = headerFactory.createViaHeader(sipUri.getHost(), sipUri.getPort(), transport, branchIdGen.get());
            } else {
                from = lastResponseOnOriginalInvite.getFrom();
                // use last response TO header, because original INVITE has no TO tag
                to = lastResponseOnOriginalInvite.getTo();
                via = (ViaHeader) getOriginalRequest().getTopmostVia().clone();
                via.setBranch(branchIdGen.get());
            }

            SipUri requestUri = (SipUri) getRemoteParty().getURI();

            SIPRequest newRequest = new SIPRequest();
            newRequest.setMethod(method);
            newRequest.setRequestURI(requestUri);
            newRequest.setHeader(via);
            newRequest.setHeader(cseq);
            newRequest.setHeader(from);
            newRequest.setHeader(to);
            newRequest.setHeader(getCallId());
            newRequest.attachHeader(new MaxForwards(MAX_FORWARDS), false);

            return newRequest;
        } catch (ParseException | InvalidArgumentException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    @Override
    public SIPResponse createProvisionalResponse(int statusCode) {
        if (!isServer()) {
            throw new IllegalStateException("Not a Server Dialog!");
        }
        if (statusCode < 100 || statusCode > 199) {
            throw new IllegalArgumentException("Bad status code " + statusCode);
        }
        return getInitialInvite().createResponse(statusCode);
    }

    @Override
    public SIPResponse createSuccessResponse(Supplier<String> uasToTagSupplier) {
        SIPRequest lastRequest = getLastRequest();
        SIPResponse response = lastRequest.createResponse(Response.OK);

        if (lastRequest.getMethod().equals("INVITE")) {
            response.setToTag(uasToTagSupplier.get());
            ContactHeader contactHeader = headerFactory.createContactHeader(getLocalParty());
            response.setHeader(contactHeader);
        }

        return response;
    }

    @Override
    public SIPRequest createAck() {
        SipTransaction lastTransaction = transactions.get(transactions.size() - 1);
        if (!(lastTransaction instanceof SipClientTransaction)) {
            throw new IllegalStateException("Only UAC can create ACK, but last transaction class = " +
                lastTransaction.getClass().getSimpleName());
        }
        // https://datatracker.ietf.org/doc/html/rfc3261#section-13.2.2.4
        if (lastTransaction.getLastResponse().isEmpty()) {
            throw new IllegalStateException("ACK can be created only after 200 response on INVITE is received.");
        }
        SIPResponse lastResponse = lastTransaction.getLastResponse().get();
        if (lastResponse.getStatusCode() != Response.OK) {
            throw new IllegalStateException("ACK can be created only for last success response.");
        }
        SIPRequest invite = lastTransaction.getOriginalRequest();
        if (!invite.getMethod().equals(Request.INVITE)) {
            throw new IllegalStateException("ACK can be created only for INVITE transaction.");
        }
        long cseqno = invite.getCSeq().getSeqNumber();
        if (cseqno <= 0) {
            throw new IllegalArgumentException("bad cseq <= 0 ");
        } else if (cseqno > ((((long) 1) << 32) - 1)) {
            throw new IllegalArgumentException("bad cseq > " + ((((long) 1) << 32) - 1));
        }

        SIPRequest sipRequest = new SIPRequest();
        sipRequest.setMethod(Request.ACK);
        sipRequest.setRequestURI(invite.getRequestURI());
        sipRequest.setCallId(invite.getCallId());
        sipRequest.setCSeq(new CSeq(cseqno, Request.ACK));
        ViaHeader via = (ViaHeader) invite.getTopmostVia().clone();
        try {
            via.setBranch(branchIdGen.get());
            sipRequest.setVia(Collections.singletonList(via));
            sipRequest.setFrom(invite.getFrom());

            sipRequest.setTo(lastResponse.getTo()); // contains To tag
            sipRequest.setMaxForwards(new MaxForwards(MAX_FORWARDS));
        } catch (ParseException | InvalidArgumentException e) {
            throw new IllegalArgumentException(e);
        }
        return sipRequest;
    }

    private SIPRequest getOriginalRequest() {
        return transactions.get(0).getOriginalRequest();
    }

    @Override
    public Address getRemoteParty() {
        if (isServer()) {
            return getInitialInvite().getFrom().getAddress();
        } else {
            return getInitialInvite().getTo().getAddress();
        }
    }

    @Override
    public Address getLocalParty() {
        if (isServer()) {
            return transactions.get(0).getOriginalRequest().getTo().getAddress();
        } else {
            return transactions.get(0).getOriginalRequest().getFrom().getAddress();
        }
    }

    private long findLastRequestCseq() {
        if (transactions.isEmpty()) {
            return 0;
        }
        SipTransaction lastTx = transactions.get(transactions.size() - 1);
        return lastTx.getOriginalRequest().getCSeq().getSeqNumber();
    }

    @Override
    public CallIdHeader getCallId() {
        return getOriginalRequest().getCallId();
    }

    @Override
    public DialogState getState() {
        return state;
    }

    @Override
    public void setApplicationData(T applicationData) {
        this.applicationData = applicationData;
    }

    @Override
    public T getApplicationData() {
        return applicationData;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        //noinspection rawtypes
        SipDialogImpl dialog = (SipDialogImpl) o;
        return getDialogId().equals(dialog.getDialogId());
    }

    @Override
    public int hashCode() {
        return getDialogId().hashCode();
    }
}
