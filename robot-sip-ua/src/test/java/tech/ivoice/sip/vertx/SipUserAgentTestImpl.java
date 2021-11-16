package tech.ivoice.sip.vertx;

import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import io.smallrye.mutiny.Uni;

import java.util.function.Supplier;

public class SipUserAgentTestImpl extends AbstractSipUserAgent<Void> {
    public SipUserAgentTestImpl(SipVerticleConfig config, Supplier<String> idGenerator) {
        super(config, new TransportMock(), idGenerator);
    }

    private static class TransportMock implements Transport {
        @Override
        public Uni<Void> asyncStartListener() {
            return Uni.createFrom().voidItem();
        }

        @Override
        public void send(SIPRequest request) {
            System.out.println("mock sending " + request);
        }

        @Override
        public void send(SIPResponse response) {
            System.out.println("mock sending " + response);
        }
    }
}
