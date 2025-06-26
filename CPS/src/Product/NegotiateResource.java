package Product;

import Utilities.Constants;
import Utilities.DFInteraction;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.WakerBehaviour;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.proto.ContractNetInitiator;

import java.sql.SQLOutput;
import java.util.Vector;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.awt.Point;

public class NegotiateResource extends ContractNetInitiator {
    String location;
    String step;
    String mode;
    ACLMessage backupMsg;
    Random random = new Random();
    Map<String, List<String>> stationSkills = new HashMap<>();

    public NegotiateResource(Agent a, ACLMessage cfp, String s, String mode) {
        super(a, cfp);
        this.location = ((ProductAgent) myAgent).location;
        this.step = s;
        this.backupMsg = cfp;
        this.mode = mode;
        initializeStationSkills();
    }

    private void initializeStationSkills() {
        stationSkills.put("Operator", Arrays.asList(Constants.SK_PICK_UP, Constants.SK_DROP));
        stationSkills.put("GlueStation1", Arrays.asList(Constants.SK_GLUE_TYPE_A, Constants.SK_GLUE_TYPE_B));
        stationSkills.put("GlueStation2", Arrays.asList(Constants.SK_GLUE_TYPE_A, Constants.SK_GLUE_TYPE_C));
        stationSkills.put("QualityControlStation1", Arrays.asList(Constants.SK_QUALITY_CHECK));
        stationSkills.put("QualityControlStation2", Arrays.asList(Constants.SK_QUALITY_CHECK));
    }

    // Method to calculate distance between two locations
    private double calculateDistance(String from, String to) {
        
        Map<String, Point> stationPositions = Map.of(
                "GlueStation1", new Point(-2, 2),
                "GlueStation2", new Point(-2, 4),
                "QualityControlStation1", new Point(2, 2),
                "QualityControlStation2", new Point(2, 4),
                "Source", new Point(0, 0)
        );

        Point p1 = stationPositions.get(from);
        Point p2 = stationPositions.get(to);

        
        if (p1 == null || p2 == null) {
            return Double.MAX_VALUE;
        }

        return Math.hypot(p1.x - p2.x, p1.y - p2.y);
    }

    // Handle response from resource negotiation
    @Override
    protected void handleInform(ACLMessage inform) {
        if(mode.equals(Constants.RESERVE_RESOURCE)) {
            if(!location.equals(inform.getContent())){
                ACLMessage releaseRes = new ACLMessage(ACLMessage.INFORM);
                releaseRes.addReceiver(new AID(inform.getContent(), AID.ISLOCALNAME));
                releaseRes.setContent(Constants.RELEASE_RESERVE);
                myAgent.send(releaseRes);
            }
            ((ProductAgent) myAgent).nextLocation = inform.getContent();
            return;
        }

        System.out.println("[" + myAgent.getLocalName() + "] Origin: " + location);
        System.out.println("[" + myAgent.getLocalName() + "] Destination: " + inform.getContent());

        ACLMessage msgDoSkill = new ACLMessage(ACLMessage.REQUEST);
        msgDoSkill.setOntology(Constants.ONTOLOGY_EXECUTE_SKILL);
        msgDoSkill.setContent(step);
        msgDoSkill.setPerformative(ACLMessage.REQUEST);
        msgDoSkill.addReceiver(inform.getSender());

        // If the destination is already reached, execute the skill
        if (inform.getContent().equalsIgnoreCase(location)) {
            System.out.println("[" + myAgent.getLocalName() + "] NegotiateResource: Already at destination; executing skill");
            myAgent.addBehaviour(new ExecuteSkill(myAgent, msgDoSkill));
        } else {
            // If a different location is detected, initiate transport negotiation
            System.out.println("[" + myAgent.getLocalName() + "] NegotiateResource: Different location detected; initiating transport negotiation");
            ACLMessage msgRequest = new ACLMessage(ACLMessage.REQUEST);
            msgRequest.setContent(location + Constants.TOKEN + inform.getContent());
            msgRequest.setConversationId(((ProductAgent) myAgent).id);
            DFInteraction dfInteraction = new DFInteraction();
            try {
                for (DFAgentDescription result : dfInteraction.SearchInDFByName(Constants.SK_MOVE, myAgent)) {
                    msgRequest.addReceiver(new AID(result.getName().getLocalName(), false));
                }
            } catch (FIPAException e) {
                e.printStackTrace();
            }
            myAgent.addBehaviour(new NegotiateTransport(myAgent, msgRequest, msgDoSkill));
        }
    }

    // Handle all responses to the negotiation
    @Override
    protected void handleAllResponses(Vector responses, Vector acceptances) {
        if (responses == null || responses.isEmpty()) {
            System.out.println("[" + myAgent.getLocalName() + "] No responses received");
            block(2000 + random.nextInt(1000));
            myAgent.addBehaviour(new NegotiateResource(myAgent, backupMsg, step, mode));
            return;
        }
        ProductAgent agent = (ProductAgent) myAgent;
        ACLMessage bestProposal = null;
        // for choice==1
        int currentBestCost = Integer.MAX_VALUE;
        double bestDistance = Double.MAX_VALUE;
        // for choice==2
        int bestConsecutiveMatch = -1;

        List<String> executionPlan = ((ProductAgent) myAgent).executionPlan;
        int currentStepIndex = executionPlan.indexOf(step);

        int choice = ((ProductAgent) myAgent).algorithmChoice;
        String selfName = myAgent.getLocalName();

        // Check if any proposal has reserved == this productAgent's name
        for (Object obj : responses) {
            ACLMessage msg = (ACLMessage) obj;

            if (msg.getPerformative() != ACLMessage.PROPOSE)
                continue;

            String[] contentParts = msg.getContent().split(Constants.TOKEN);
            if (contentParts.length >= 3) {
                String resourceReserved = contentParts[2];
                if (selfName.equals(resourceReserved)) {
                    System.out.println("[" + myAgent.getLocalName() + "] Applying the reservation");
                    bestProposal = msg;
                    break;
                }
            }
        }

        if (bestProposal == null) {
            // Select the best proposal normally
            for (Object obj : responses) {
                ACLMessage msg = (ACLMessage) obj;
                if (msg.getPerformative() != ACLMessage.PROPOSE)
                    continue;

                if (choice == 1) {
                    String[] contentParts = msg.getContent().split(Constants.TOKEN);
                    int cost = Integer.parseInt(contentParts[0]);
                    String proposedLocation = contentParts[1];
                    // (contentParts[2] is resourceReserved)

                    double distance = calculateDistance(location, proposedLocation);
                    double combined = cost + distance;
                    double bestCombined = currentBestCost + bestDistance;
                    System.out.println("[" + myAgent.getLocalName() + "] Proposal from " + msg.getSender().getLocalName()
                            + ": cost=" + cost + ", dist=" + String.format("%.2f", distance)
                            + ", combined=" + String.format("%.2f", combined) + ", mode: " + mode);

                    if (bestProposal == null || combined < bestCombined) {
                        bestProposal = msg;
                        currentBestCost = cost;
                        bestDistance = distance;
                    }
                } else if (choice == 2) {
                    String senderName = msg.getSender().getLocalName();
                    List<String> providedSkills = stationSkills.get(senderName);

                    int consecutiveMatchCount = 0;
                    if (providedSkills != null && currentStepIndex != -1) {
                        for (int i = currentStepIndex; i < executionPlan.size(); i++) {
                            String neededSkill = executionPlan.get(i);
                            if (providedSkills.contains(neededSkill)) {
                                consecutiveMatchCount++;
                            } else {
                                break;
                            }
                        }
                    }

                    if (consecutiveMatchCount > bestConsecutiveMatch) {
                        bestProposal = msg;
                        bestConsecutiveMatch = consecutiveMatchCount;
                    }
                } else {
                    throw new IllegalStateException("Unknown choice: " + choice);
                }
            }
        }

        for (Object obj : responses) {
            ACLMessage msg = (ACLMessage) obj;
            if (msg.getPerformative() == ACLMessage.PROPOSE) {
                ACLMessage reply = msg.createReply();
                if (msg == bestProposal) {
                    reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                    reply.setContent(step + Constants.TOKEN + ((ProductAgent) myAgent).useCoppelia);
                    acceptances.add(reply);

                    System.out.println("[" + myAgent.getLocalName() + "] NegotiateResource: Accepting proposal from " + msg.getSender().getLocalName());

                    if(mode.equals(Constants.RESERVE_RESOURCE)) {
                        if (agent.current_state == 0) {
                            myAgent.addBehaviour(new SearchResource(myAgent, agent.executionPlan.get(agent.current_state), Constants.SELECT_RESOURCE));
                        }
                    } else {
                        if (executionPlan.size() > ((ProductAgent) myAgent).current_state + 1){
                            String nextSkill = executionPlan.get(((ProductAgent) myAgent).current_state + 1);
                            if (!executionPlan.get(((ProductAgent) myAgent).current_state).equals(Constants.SK_PICK_UP) &&
                                    ((ProductAgent) myAgent).current_state + 1 < ((ProductAgent) myAgent).executionPlan.size()) {
                                myAgent.addBehaviour(new SearchResource(myAgent, nextSkill, Constants.RESERVE_RESOURCE));
                            }
                        }
                    }


                } else {
                    reply.setPerformative(ACLMessage.REJECT_PROPOSAL);
                    acceptances.add(reply);
                }
            }
        }

        // Retry if no valid proposals were received
        if (bestProposal == null) {
            myAgent.addBehaviour(new WakerBehaviour(myAgent, 1000 + random.nextInt(500)) {
                @Override
                protected void onWake() {
                    myAgent.addBehaviour(new NegotiateResource(myAgent, backupMsg, step, mode));
                }
            });
        }
    }
}
