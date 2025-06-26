package Transport;

import Utilities.Constants;
import jade.core.Agent;
import jade.domain.FIPAAgentManagement.NotUnderstoodException;
import jade.domain.FIPAAgentManagement.RefuseException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.AchieveREResponder;

public class TransportAchiever extends AchieveREResponder {
    public TransportAchiever(Agent a, MessageTemplate mt) {
        super(a, mt);
    }

    @Override
    protected ACLMessage handleRequest(ACLMessage request)throws RefuseException, NotUnderstoodException {
        ACLMessage reply = request.createReply();
        reply.setPerformative(ACLMessage.AGREE);
        String[] parts = request.getContent().split(Constants.TOKEN);

        ((TransportAgent)myAgent).myLib.executeMove(parts[0], parts[1], request.getConversationId());

        return reply;
    }

    @Override
    protected ACLMessage prepareResultNotification(ACLMessage request, ACLMessage response) {
        ACLMessage reply = request.createReply();
        reply.setPerformative(ACLMessage.INFORM);
        String[] parts = request.getContent().split(Constants.TOKEN);
        reply.setContent(parts[1]);
        return reply;
    }
}
