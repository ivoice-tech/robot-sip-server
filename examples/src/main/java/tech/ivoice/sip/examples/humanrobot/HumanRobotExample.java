package tech.ivoice.sip.examples.humanrobot;

import gov.nist.javax.sip.address.AddressFactoryImpl;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.mutiny.core.Vertx;
import tech.ivoice.sip.vertx.SipVerticleConfig;

import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import java.text.ParseException;

public class HumanRobotExample extends AbstractVerticle {
    public static void main(String[] args) {
        var vertx = Vertx.vertx();
        vertx.deployVerticleAndAwait(new HumanRobotExample());
    }

    @Override
    public Uni<Void> asyncStart() {
        SipVerticleConfig humanConfig = new SipVerticleConfig("127.0.0.1", 5080, "udp");
        SipVerticleConfig robotConfig = new SipVerticleConfig("127.0.0.1", 5081, "udp");

        SipURI robotSipUri;
        AddressFactory addressFactory = new AddressFactoryImpl();
        try {
            robotSipUri = addressFactory.createSipURI("Robot", "127.0.0.1:5081");
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
        Human human = new Human(humanConfig, robotSipUri);
        Robot robot = new Robot(robotConfig);

        return vertx.deployVerticle(robot, new DeploymentOptions()).replaceWithVoid()
            .onItem()
            .invoke(shootmeDeployed -> vertx.deployVerticleAndForget(human, new DeploymentOptions()));
    }
}
