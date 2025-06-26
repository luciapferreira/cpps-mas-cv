package Order;

import Product.ProductAgent;
import Utilities.Constants;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;

import java.util.*;

public class OrderAgent extends Agent {
    int productA;
    int productB;
    int productC;
    int algorithmChoice;    // 1=Cost+Distance, 2=Skill Matches
    int defectChoice;       // 1=Repeat last executed skill, 2=Match defect location
    public boolean useCoppelia;

    private int productCounter = 0;

    @Override
    protected void setup() {
        Object[] args = this.getArguments();
        this.productA = (int) args[0];
        this.productB = (int) args[1];
        this.productC = (int) args[2];

        // Read algorithm choice passed from ConsoleFrame
        if (args.length > 3 && args[3] instanceof Integer) {
            this.algorithmChoice = (int) args[3];
        } else {
            this.algorithmChoice = 1;  // default
        }

        if (args.length > 4 && args[4] instanceof Integer) {
            this.defectChoice = (int) args[4];
        } else {
            this.defectChoice = 1;  // default
        }

        if (args.length > 5 && args[5] instanceof Boolean) {
            this.useCoppelia = (Boolean) args[5];
        } else {
            this.useCoppelia = false;
        }

        System.out.println("Order Received:" +
                " ProductsA=" + productA +
                " ProductsB=" + productB +
                " ProductsC=" + productC +
                " AlgorithmChoice=" + algorithmChoice +
                " DefectChoice=" + defectChoice +
                " useCoppelia=" + useCoppelia);

        addBehaviour(new CreateProductBehaviour());
    }

    @Override
    protected void takeDown() {}

    private class CreateProductBehaviour extends OneShotBehaviour {
        @Override
        public void action() {
            List<String> mix = getProductionOrder();

            // 1) Separate C products and run them one‐by‐one
            List<String> firstC = new ArrayList<>(), rest = new ArrayList<>();
            for (String p : mix) {
                if (p.equals("C"))
                    firstC.add(p);
                else
                    rest.add(p);
            }

            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
            Random rnd = new Random();

            for (String p : firstC) {
                launchProduct(p);
                ACLMessage reply = blockingReceive(mt);
                System.out.println("OrderAgent: Received completion from " + reply.getContent());
                try { Thread.sleep(rnd.nextInt(500)); } catch (InterruptedException ignored) {}
            }

            // Process A/B with up to 2 in flight at once
            System.out.println("Now launching remaining A/B products, max concurrency = 2");
            List<String> queue = new ArrayList<>(rest);
            Set<String> inflight = new HashSet<>();

            // Kick off up to two immediately
            while (inflight.size() < 2 && !queue.isEmpty()) {
                String p = queue.remove(0);
                String id = launchProduct(p);
                inflight.add(id);
                System.out.println("Launched product " + id + " of type " + p + " (inflight=" + inflight + ")");
                try { Thread.sleep(rnd.nextInt(500)); } catch (InterruptedException ignored) {}
            }

            while (!inflight.isEmpty()) {
                ACLMessage done = blockingReceive(mt);
                String content = done.getContent();   // e.g. "Product3_DONE"
                System.out.println("OrderAgent: Received completion from " + content);

                // Remove from inflight
                inflight.remove(content.replace("_DONE",""));

                // Launch one more if available
                if (!queue.isEmpty()) {
                    String p = queue.remove(0);
                    String id = launchProduct(p);
                    inflight.add(id);
                    System.out.println("Launched product " + id + " of type " + p + " (inflight=" + inflight + ")");
                }
            }

            System.out.println("All products completed.");
        }
    }

    private List<String> getProductionOrder() {
        List<String> mix = new ArrayList<>();
        int aCount = productA, bCount = productB, cCount = productC;
        int total = aCount + bCount + cCount;

        while (mix.size() < total) {
            if (aCount-- > 0) mix.add("A");
            if (bCount-- > 0) mix.add("B");
            if (cCount-- > 0) mix.add("C");
        }
        return mix;
    }

    /**
     * Instantiates and starts a ProductAgent of the given type,
     * passing this OrderAgent’s name so the product can notify us when done.
     *
     * @param productType  "A", "B", or "C"
     * @return the new agent’s local name
     */
    private String launchProduct(String productType) {
        String id = "Product" + (productCounter++);
        try {
            ProductAgent pa = new ProductAgent();
            pa.setArguments(new Object[]{
                    id,
                    productType,
                    algorithmChoice,
                    defectChoice,
                    useCoppelia,
                    getLocalName()   // pass this OrderAgent’s name
            });
            AgentController ac = getContainerController().acceptNewAgent(id, pa);
            ac.start();
        } catch (StaleProxyException e) {
            e.printStackTrace();
        }
        return id;
    }

}
