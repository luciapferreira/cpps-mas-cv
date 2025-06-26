package Resource;

import Utilities.Constants;
import jade.core.Agent;
import jade.domain.FIPAAgentManagement.FailureException;
import jade.domain.FIPAAgentManagement.NotUnderstoodException;
import jade.domain.FIPAAgentManagement.RefuseException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.ContractNetResponder;

public class ResourceContract extends ContractNetResponder {
    private String location;

    public ResourceContract(Agent a, MessageTemplate mt, String location) {
        super(a, mt);
        this.location = location;
    }

    @Override
    protected ACLMessage handleCfp(ACLMessage cfp) throws RefuseException, FailureException, NotUnderstoodException {
        String[] contentParts = cfp.getContent().split(Constants.TOKEN);
        String mode = contentParts[0];
        String step = contentParts[1];
        ACLMessage reply = cfp.createReply();
        reply.setOntology(Constants.ONTOLOGY_EXECUTE_SKILL);

        int cost = 1;
        String reserved = ((ResourceAgent) myAgent).reserved;
        String current = ((ResourceAgent) myAgent).current_product;
        String content = cost + Constants.TOKEN + location + Constants.TOKEN + ((ResourceAgent) myAgent).reserved;
        String sender = cfp.getSender().getLocalName();
        // System.out.println("[" + myAgent.getLocalName() + "] Current product is " + ((ResourceAgent) myAgent).current_product);
        // System.out.println("[" + myAgent.getLocalName() + "] Current reserved product is " + ((ResourceAgent) myAgent).reserved);
        // System.out.println("[" + myAgent.getLocalName() + "] Received from " + sender);

        if(step.equals(Constants.SK_DROP)){
            reply.setPerformative(ACLMessage.PROPOSE);
            reply.setContent(content);
        } else if(!reserved.equals("None") && !reserved.equals(sender)){
            // System.out.println("[" + myAgent.getLocalName() + "] Reserved by someone else");
            reply.setPerformative(ACLMessage.REFUSE);
        } else if(reserved.equals(sender)){
            // System.out.println("[" + myAgent.getLocalName() + "] Already reserved by " + sender);
            reply.setPerformative(ACLMessage.PROPOSE);
            reply.setContent(content);
        } else if (current.equals("None") || current.equals(sender)) {
            // System.out.println("[" + myAgent.getLocalName() + "] Let's try propose");
            reply.setPerformative(ACLMessage.PROPOSE);
            reply.setContent(content);
        } else {
            reply.setPerformative(ACLMessage.REFUSE);
        }
        return reply;
    }


    @Override
    protected ACLMessage handleAcceptProposal(ACLMessage cfp, ACLMessage propose, ACLMessage accept) throws FailureException {
        System.out.println("[" + myAgent.getLocalName() + "] Accepting Proposal");

        String[] contentParts = cfp.getContent().split(Constants.TOKEN);
        String mode = contentParts[0];
        String step = contentParts[1];

        if (mode.equals(Constants.SELECT_RESOURCE) && !step.equals(Constants.SK_DROP) ) {
            ((ResourceAgent) myAgent).current_product = cfp.getSender().getLocalName();
            // System.out.println("[" + myAgent.getLocalName() + "] Current product " + ((ResourceAgent) myAgent).current_product);
        } else if(!myAgent.getLocalName().equals("Operator")){
            ((ResourceAgent) myAgent).reserved = cfp.getSender().getLocalName();
        }


        ACLMessage reply = cfp.createReply();
        reply.setPerformative(ACLMessage.INFORM);
        reply.setContent(location);
        return reply;
    }
}