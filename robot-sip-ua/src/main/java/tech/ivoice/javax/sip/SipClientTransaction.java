package tech.ivoice.javax.sip;

import javax.sip.message.Request;

public interface SipClientTransaction extends SipTransaction {
    // https://datatracker.ietf.org/doc/html/rfc3261#section-8.1.1.6
    int MAX_FORWARDS = 70;

    // TODO use this method for client tx
    Request createCancel();
}
