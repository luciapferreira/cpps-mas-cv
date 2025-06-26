package Resource;

import Utilities.Constants;
import Utilities.DFInteraction;
import jade.core.Agent;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import Libraries.IResource;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.MessageTemplate;

/**
 *
 * @author Ricardo Silva Peres <ricardo.peres@uninova.pt>
 */
public class ResourceAgent extends Agent {

    public String current_product = "None";

    String id;
    IResource myLib;
    String description;
    String[] associatedSkills;
    String location;
    String reserved = "None";

    @Override
    protected void setup() {
        Object[] args = this.getArguments();
        this.id = (String) args[0];
        this.description = (String) args[1];

        //Load hw lib
        try {
            String className = "Libraries." + (String) args[2];
            Class<?> cls = Class.forName(className);
            Object instance = cls.getDeclaredConstructor().newInstance();
            myLib = (IResource) instance;
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException |
                 InvocationTargetException ex) {
            Logger.getLogger(ResourceAgent.class.getName()).log(Level.SEVERE, null, ex);
        }

        if (getLocalName().equals("Operator")) {
            this.location = "Source";
        } else {
            this.location = getLocalName();
        }

        System.out.println("[" + getLocalName() + "] Location: " + location);

        myLib.init(this);
        this.associatedSkills = myLib.getSkills();
        System.out.println("[" + getLocalName() + "] Resource Deployed: " + this.id + " Executes: " + Arrays.toString(associatedSkills));

        try {
            DFInteraction.RegisterInDF(this, associatedSkills, Constants.DFSERVICE_RESOURCE);
        } catch (FIPAException e) {
            throw new RuntimeException(e);
        }

        this.addBehaviour(new ResourceContract(this, MessageTemplate.MatchPerformative(ACLMessage.CFP), location));
        this.addBehaviour(new ResourceAchiever(this, MessageTemplate.MatchPerformative(ACLMessage.REQUEST), myLib));
        this.addBehaviour(new ReleaseOccupancyBehaviour(this));
        this.addBehaviour(new SkillExecutionBehaviour(this));

    }

    @Override
    protected void takeDown() {
        super.takeDown(); 
    }

    private class ReleaseOccupancyBehaviour extends CyclicBehaviour {
        private MessageTemplate mt;

        public ReleaseOccupancyBehaviour(Agent a) {
            super(a);
            // Build two content‐match templates
            MessageTemplate releaseOcc = MessageTemplate.MatchContent(Constants.RELEASE_OCCUPANCY);
            MessageTemplate releaseRes = MessageTemplate.MatchContent(Constants.RELEASE_RESERVE);
            // Or them together, then AND with performative=INFORM
            mt = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                    MessageTemplate.or(releaseOcc, releaseRes)
            );
        }

        @Override
        public void action() {
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                String content = msg.getContent();

                ResourceAgent self = (ResourceAgent) myAgent;

                if (Constants.RELEASE_OCCUPANCY.equals(content)) {
                    // Free both occupancy and any reservation, if you want
                    self.current_product = "None";
                    System.out.println("[" + self.getLocalName() + "] Released occupancy.");
                }
                if (Constants.RELEASE_RESERVE.equals(content)) {
                    // Only clear the reservation
                    self.reserved = "None";
                    System.out.println("[" + self.getLocalName() + "] Cleared reservation.");
                }

            } else {
                block(1000);
            }
        }
    }

    private class SkillExecutionBehaviour extends CyclicBehaviour {
        private MessageTemplate mt;

        public SkillExecutionBehaviour(Agent a) {
            super(a);
            mt = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
                    MessageTemplate.MatchOntology(Constants.ONTOLOGY_EXECUTE_SKILL)
            );
        }

        @Override
        public void action() {
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {

                String content = msg.getContent();

                String[] parts = content.split(Constants.TOKEN, 2);

                String skill = parts[0];

                boolean useCoppelia = false;
                if (parts.length == 2) {
                     useCoppelia = Boolean.parseBoolean(parts[1]);
                }

                int success = myLib.executeSkill(skill, useCoppelia);

                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.INFORM);

                String text = switch (success) {
                    case -1 -> "failed";
                    case 1 -> "successful";
                    case 2 -> "upper";
                    case 3 -> "bottom";
                    default -> "unknown";
                };
                reply.setContent(text);
                myAgent.send(reply);
            } else {
                block(1000);
            }
        }
    }
}
