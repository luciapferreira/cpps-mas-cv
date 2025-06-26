package Resource;

import Libraries.IResource;
import Utilities.Constants;
import jade.core.Agent;
import jade.domain.FIPAAgentManagement.FailureException;
import jade.domain.FIPAAgentManagement.RefuseException;
import jade.domain.FIPAAgentManagement.NotUnderstoodException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.AchieveREResponder;
import Resource.ResourceAgent;

public class ResourceAchiever extends AchieveREResponder {
    public ResourceAchiever(Agent a, MessageTemplate mt,  IResource myLib) {
        super(a, mt);
    }

    @Override
    protected ACLMessage handleRequest(ACLMessage request) throws NotUnderstoodException, RefuseException {
        ACLMessage reply = request.createReply();
        reply.setPerformative(ACLMessage.AGREE);
        return reply;
    }

    @Override
    protected ACLMessage prepareResultNotification(ACLMessage request, ACLMessage response) throws FailureException {
        ACLMessage reply = request.createReply();
        reply.setPerformative(ACLMessage.INFORM);

        String skill = request.getContent();
        System.out.println("[" + myAgent.getLocalName() + "] Skill " + skill + " acknowledged");

        reply.setContent("successful"); // Assume success; actual execution is done in ExecuteSkill
        return reply;
    }
}
