package Product;

import Resource.ResourceAgent;
import Utilities.Constants;
import jade.core.AID;
import jade.core.Agent;
import jade.lang.acl.ACLMessage;
import jade.proto.AchieveREInitiator;

public class NegotiateTransport extends AchieveREInitiator {
    ACLMessage msgDoSkill;
    String originalLocation;

    public NegotiateTransport(Agent a, ACLMessage msg, ACLMessage doSkill) {
        super(a, msg);
        this.msgDoSkill = doSkill;
        this.originalLocation = ((ProductAgent) a).location;
    }

    @Override
    protected void handleAgree(ACLMessage agree) {}

    @Override
    protected void handleInform(ACLMessage inform) {
        System.out.println("[" + myAgent.getLocalName() + "] NegotiateTransport: Received transport location update");
        ((ProductAgent) myAgent).location = inform.getContent();

        String releaseTarget = originalLocation.equals("Source") ? "Operator" : originalLocation;
        if (msgDoSkill.equals(Constants.SK_DROP)) {
            myAgent.addBehaviour(new ExecuteSkill(myAgent, msgDoSkill));
            return;
        }
        // Send release message to original ResourceAgent
        ACLMessage releaseMsg = new ACLMessage(ACLMessage.INFORM);
        releaseMsg.addReceiver(new AID(releaseTarget, AID.ISLOCALNAME));
        releaseMsg.setPerformative(ACLMessage.INFORM);

        releaseMsg.setContent(Constants.RELEASE_OCCUPANCY);
        myAgent.send(releaseMsg);

        myAgent.doWait(1000);

        myAgent.addBehaviour(new ExecuteSkill(myAgent, msgDoSkill));
    }
}
