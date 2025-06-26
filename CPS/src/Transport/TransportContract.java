package Transport;

import Utilities.Constants;
import jade.core.Agent;
import jade.domain.FIPAAgentManagement.FailureException;
import jade.domain.FIPAAgentManagement.NotUnderstoodException;
import jade.domain.FIPAAgentManagement.RefuseException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.ContractNetResponder;

public class TransportContract extends ContractNetResponder {
    String[] associatedSkills;

    public TransportContract(Agent a, MessageTemplate mt, String[] associatedSkills) {
        super(a, mt);
        this.associatedSkills = associatedSkills;
    }

    @Override
    protected ACLMessage handleCfp(ACLMessage cfp) throws RefuseException, FailureException, NotUnderstoodException {
        System.out.println("TA_handleCfp");
        ACLMessage reply = cfp.createReply();
        reply.setPerformative(ACLMessage.PROPOSE);
        reply.setContent(Double.toString(Math.random()));
        reply.setOntology(Constants.ONTOLOGY_NEGOTIATE_RESOURCE);
        return reply;
    }

    @Override
    protected ACLMessage handleAcceptProposal(ACLMessage cfp, ACLMessage propose, ACLMessage accept) throws FailureException {
        System.out.println(myAgent.getLocalName() + ":  Preparing result of CFP : Transport");
        System.out.println("MOVE");
        ACLMessage msg = cfp.createReply();
        msg.setPerformative(ACLMessage.INFORM);
        msg.setContent(String.valueOf(associatedSkills));
        return msg;
    }
}
