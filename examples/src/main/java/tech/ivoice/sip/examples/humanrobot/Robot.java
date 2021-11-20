package tech.ivoice.sip.examples.humanrobot;

import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import io.smallrye.mutiny.Uni;
import tech.ivoice.sip.vertx.AbstractSipUserAgent;
import tech.ivoice.sip.vertx.SipVerticleConfig;

import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Robot extends AbstractSipUserAgent<List<Robot.QA>> {
    private final String user = "Robot";

    public Robot(SipVerticleConfig config) {
        super(config);
    }

    @Override
    public void onInvite(SIPRequest invite) {
        String callId = invite.getCallId().getCallId();

        SIPResponse provisionalResponse = createTryingResponse(callId);
        sendResponse(provisionalResponse);

        initDialogScenario(invite);
    }

    private void initDialogScenario(SIPRequest invite) {
        log.info(user + ": initializing scenario...");
        // emulate delay 1 sec for scenario initialization
        Uni.createFrom().voidItem()
            .onItem().delayIt().by(Duration.of(1, ChronoUnit.SECONDS))
            .map(itsTime -> createSuccessResponse(invite.getCallId().getCallId()))
            .subscribe().with(response -> {
                setApplicationData(invite.getCallId().getCallId(), new ArrayList<>());
                log.info(user + ": scenario initialized, sending OK");
                sendResponse(response);
            });
    }

    @Override
    protected void onServerDialogConfirmed(SIPRequest ack) {
        log.info(user + ": dialog confirmed, asking Human question...");
        SIPRequest request = nextQuestion(ack.getCallId().getCallId()).orElseThrow();
        sendRequest(request);
    }

    @Override
    protected void onMessage(SIPRequest message) {
        String callId = message.getCallId().getCallId();
        try {
            log.info(user + ": Human responded: " + message.getMessageContent());
            List<QA> applicationData = getApplicationData(callId);
            applicationData.get(applicationData.size() - 1).answer = message.getMessageContent();
            Optional<SIPRequest> maybeQuestion = nextQuestion(callId);
            if (maybeQuestion.isPresent()) {
                sendRequest(maybeQuestion.get());
            } else {
                log.info(user + ": Collected answers: " + applicationData);
                SIPRequest bye = createBye(callId);
                sendRequest(bye);
            }
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    protected void onDialogTerminated(SIPResponse successResponseOnBye) {
        log.info(user + ": dialog terminated");
    }

    private Optional<SIPRequest> nextQuestion(String callId) {
        List<QA> applicationData = getApplicationData(callId);
        if (applicationData.isEmpty()) {
            applicationData.add(new QA("name"));
            return Optional.of(createMessage(callId, "What is your name?"));
        } else if (applicationData.size() == 1) {
            applicationData.add(new QA("activity"));
            return Optional.of(createMessage(callId, "What is your favorite activity?"));
        } else {
            // no more question
            return Optional.empty();
        }
    }

    public static final class QA {
        private final String question;
        private String answer;

        public QA(String question) {
            this.question = question;
        }

        @Override
        public String toString() {
            return "QA{" +
                "q='" + question + '\'' +
                ", a='" + answer + '\'' +
                '}';
        }
    }
}
