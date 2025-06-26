package Product;

import Utilities.Constants;
import Utilities.DFInteraction;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.SimpleBehaviour;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

import java.util.Objects;

public class SearchResource extends SimpleBehaviour {
    private boolean goSearch = true;
    private String step;
    private String mode;

    public SearchResource(Agent a, String s, String mode) {
        super(a);
        this.step = s;
        this.mode = mode;
    }

    @Override
    public void action() {
        if (!goSearch) {
            return;
        }

        ProductAgent self = (ProductAgent) myAgent;
        // System.out.println("[" + myAgent.getLocalName() + "] Execution plan: " + self.executionPlan + " | Mode: " + mode);
        System.out.println("[" + myAgent.getLocalName() + "] Searching for: " + step);
        int lastIndex = self.current_state - 1;
        boolean forceStation = false;
        if (lastIndex >= 2) {
            String previousStep = self.executionPlan.get(lastIndex);
            if (Constants.SK_QUALITY_CHECK.equals(previousStep) && !step.equals(Constants.SK_DROP)) {
                forceStation = true;
            }
        }

        ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
        cfp.setOntology(Constants.ONTOLOGY_NEGOTIATE_RESOURCE);
        cfp.setContent(mode + Constants.TOKEN+ step);

        try {
            if (forceStation && ((ProductAgent) myAgent).defectChoice == 2) {
                if (step.equals(Constants.SK_GLUE_TYPE_A) || step.equals(Constants.SK_GLUE_TYPE_B)) {
                    cfp.addReceiver(new AID("GlueStation1", AID.ISLOCALNAME));
                    System.out.println("[" + myAgent.getLocalName() + "] Forcing " + myAgent.getLocalName() + " to GlueStation1");
                }
                else {
                    cfp.addReceiver(new AID("GlueStation2", AID.ISLOCALNAME));
                    System.out.println("[" + myAgent.getLocalName() + "] Forcing " + myAgent.getLocalName() + " to GlueStation2");
                }
            } else {
                for (DFAgentDescription dfd : DFInteraction.SearchInDFByName(step, myAgent)) {
                    String agentName = dfd.getName().getLocalName();
                    cfp.addReceiver(new AID(agentName, AID.ISLOCALNAME));
                }
            }
        } catch (FIPAException e) {
            e.printStackTrace();
        }

        myAgent.addBehaviour(new NegotiateResource(myAgent, cfp, step, mode));
        goSearch = false;
    }

    @Override
    public boolean done() {
        ProductAgent agent = (ProductAgent) myAgent;

        if (mode.equals(Constants.RESERVE_RESOURCE)) {
            return true;
        }

        if (agent.go_next_step) {
            System.out.println("[" + myAgent.getLocalName() + "] Moving to next step...");
            // Proceed to the next step in the execution plan.
            agent.current_state++;

            // If the plan is complete, terminate the agent.
            if (agent.executionPlan.size() <= agent.current_state) {
                myAgent.doDelete();
                return true;
            }

            // Clear flag.
            agent.go_next_step = false;
            agent.go_prev_step = false;

            // Schedule the behaviour for the next step.
            if (mode.equals(Constants.SELECT_RESOURCE)) {
                myAgent.addBehaviour(new SearchResource(myAgent, agent.executionPlan.get(agent.current_state), mode));
            }
            return true;
        }
        if (agent.go_prev_step) {
            System.out.println("[" + myAgent.getLocalName() + "] Moving to previous step...");
            // Go back to the previous step.
            agent.current_state--;
            agent.go_prev_step = false;

            // Schedule the behaviour for the previous step.
            if (mode.equals(Constants.SELECT_RESOURCE)) {
                myAgent.addBehaviour(new SearchResource(myAgent, agent.executionPlan.get(agent.current_state), mode));
            }
            return true;
        }
        return false;
    }
}
