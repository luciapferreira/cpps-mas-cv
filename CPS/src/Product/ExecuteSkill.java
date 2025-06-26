package Product;

import Utilities.Constants;
import jade.core.AID;
import jade.core.Agent;
import jade.lang.acl.ACLMessage;
import jade.proto.AchieveREInitiator;


public class ExecuteSkill extends AchieveREInitiator {
    String skill;
    AID targetResource;

    public ExecuteSkill(Agent a, ACLMessage msg) {
        super(a, msg);
        this.skill = msg.getContent();
        this.targetResource = (AID) msg.getAllReceiver().next(); // Get the target resource
    }

    @Override
    protected void handleInform(ACLMessage inform) {
        // Send REQUEST to execute the skill
        ACLMessage executeMsg = new ACLMessage(ACLMessage.REQUEST);
        executeMsg.setContent(skill + Constants.TOKEN + ((ProductAgent) myAgent).useCoppelia);
        executeMsg.setOntology(Constants.ONTOLOGY_EXECUTE_SKILL);
        executeMsg.addReceiver(targetResource);


        myAgent.addBehaviour(new AchieveREInitiator(myAgent, executeMsg) {
            @Override
            protected void handleInform(ACLMessage result) {
                String reply = result.getContent();
                if (reply.equals("successful")) {
                    ((ProductAgent) myAgent).go_next_step = true;
                    ((ProductAgent) myAgent).go_prev_step = false;
                }
                else {
                    ProductAgent pa = (ProductAgent) myAgent;

                    if (pa.defectChoice == 1) {
                        pa.executionPlan.add(pa.current_state + 1, pa.executionPlan.get(pa.current_state - 1));
                        pa.go_next_step = true;
                        pa.go_prev_step = false;
                    } else {
                        String addSkill = calculateNextSkill(pa, reply);
                        pa.executionPlan.add(pa.current_state + 1, addSkill);
                        pa.go_next_step = true;
                        pa.go_prev_step = false;
                    }
                }
            }
        });
    }

    private String calculateNextSkill(ProductAgent pa, String reply) {
        if ("upper".equals(reply)) {
            return Constants.SK_GLUE_TYPE_A;
        }
        return pa.executionPlan.contains(Constants.SK_GLUE_TYPE_B)
                ? Constants.SK_GLUE_TYPE_B
                : Constants.SK_GLUE_TYPE_C;
    }
}