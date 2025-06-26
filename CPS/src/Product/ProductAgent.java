package Product;

import Utilities.Constants;
import Utilities.DFInteraction;
import jade.core.AID;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

import java.util.ArrayList;

/**
 *
 * @author Ricardo Silva Peres <ricardo.peres@uninova.pt>
 */
public class ProductAgent extends Agent {

    // Agent properties
    public String id;
    public ArrayList<String> executionPlan = new ArrayList<>();
    public int current_state = 0;
    public String location;
    public String nextLocation;
    public int algorithmChoice = 1;
    public int defectChoice = 1;
    public boolean useCoppelia = false;
    private String orderAgentName;

    // Flags to control step transitions
    public boolean go_next_step = false;
    public boolean go_prev_step = false;

    @Override
    protected void setup() {
        Object[] args = this.getArguments();
        if (args != null && args.length >= 2) {
            this.id = (String) args[0];
            this.executionPlan = getExecutionList((String) args[1]);
            this.location = "Source";
            this.nextLocation = "None";
            this.algorithmChoice = (int) args[2];
            this.defectChoice = (int) args[3];
            this.useCoppelia = (boolean) args[4];
            this.orderAgentName = (String) args[5];
        } else {
            System.out.println("[" + getLocalName() + "] Missing arguments for ProductAgent. Shutting down.");
            doDelete();
            return;
        }
        System.out.println("[" + getLocalName() + "] Product launched: " + id + " Requires: " + executionPlan);

        try {
            DFInteraction.RegisterInDF(this, id, Constants.DFSERVICE_PRODUCT);
        } catch (FIPAException e) {
            throw new RuntimeException(e);
        }

        this.addBehaviour(new SearchResource(this, executionPlan.get(current_state + 1), Constants.RESERVE_RESOURCE));
    }

    @Override
    protected void takeDown() {
        ACLMessage done = new ACLMessage(ACLMessage.INFORM);
        done.addReceiver(new AID(orderAgentName, AID.ISLOCALNAME));
        done.setContent(getLocalName() + "_DONE");
        send(done);
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        System.out.println("ProductAgent " + id + " is shutting down.");
        super.takeDown();
    }

    // Returns an execution plan for the product based on its type.
    private ArrayList<String> getExecutionList(String productType) {
        switch (productType) {
            case "A":
                return Utilities.Constants.PROD_A;
            case "B":
                return Utilities.Constants.PROD_B;
            case "C":
                return Utilities.Constants.PROD_C;
        }
        return new ArrayList<>();
    }
}


