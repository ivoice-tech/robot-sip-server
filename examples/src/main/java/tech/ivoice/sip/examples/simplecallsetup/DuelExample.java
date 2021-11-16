package tech.ivoice.sip.examples.simplecallsetup;

import gov.nist.javax.sip.address.AddressFactoryImpl;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.mutiny.core.Vertx;
import tech.ivoice.sip.vertx.SipVerticleConfig;

import javax.sip.address.SipURI;
import java.text.ParseException;

public class DuelExample extends AbstractVerticle {
    public static void main(String[] args) {
        var vertx = Vertx.vertx();
        vertx.deployVerticleAndAwait(new DuelExample());
    }

    @Override
    public Uni<Void> asyncStart() {
        SipVerticleConfig shootistConfig = new SipVerticleConfig("127.0.0.1", 5080, "udp");
        SipVerticleConfig shootmeConfig = new SipVerticleConfig("127.0.0.1", 5081, "udp");

        Shootme shootme = new Shootme(shootmeConfig, false);

        SipURI shootmeSipUri;
        try {
            shootmeSipUri = new AddressFactoryImpl().createSipURI("LittleGuy", "127.0.0.1:5081");
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
        Shootist shootist = new Shootist(shootistConfig, shootmeSipUri);

        return vertx.deployVerticle(shootme, new DeploymentOptions()).replaceWithVoid()
            .onItem()
            .invoke(shootmeDeployed -> vertx.deployVerticleAndForget(shootist, new DeploymentOptions()));
    }
}
