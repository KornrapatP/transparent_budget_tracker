package com.template;

import com.google.common.collect.ImmutableList;
import com.template.flows.ApproveFlow;
import com.template.flows.RequestFlow;
import com.template.states.TransferState;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.identity.CordaX500Name;
import net.corda.testing.core.TestIdentity;
import net.corda.testing.driver.DriverParameters;
import net.corda.testing.driver.NodeHandle;
import net.corda.testing.driver.NodeParameters;
import org.junit.Test;

import java.util.List;

import static net.corda.testing.driver.Driver.driver;
import static org.junit.Assert.assertEquals;

public class DriverBasedTest {
    private final TestIdentity partyA = new TestIdentity(new CordaX500Name("PartyA", "", "GB"));
    private final TestIdentity partyB = new TestIdentity(new CordaX500Name("PartyB", "", "US"));
    private final TestIdentity partyC = new TestIdentity(new CordaX500Name("PartyC", "", "TH"));

    @Test
    public void nodeTest() {
        driver(new DriverParameters().withIsDebug(true).withStartNodesInProcess(true), dsl -> {

            // This starts three nodes simultaneously with startNode, which returns a future that completes when the node
            // has completed startup. Then these are all resolved with getOrThrow which returns the NodeHandle list.
            List<CordaFuture<NodeHandle>> handleFutures = ImmutableList.of(
                    dsl.startNode(new NodeParameters().withProvidedName(partyA.getName())),
                    dsl.startNode(new NodeParameters().withProvidedName(partyB.getName())),
                    dsl.startNode(new NodeParameters().withProvidedName(partyC.getName()))
            );

            try {
                NodeHandle partyAHandle = handleFutures.get(0).get();
                NodeHandle partyBHandle = handleFutures.get(1).get();
                NodeHandle partyCHandle = handleFutures.get(2).get();

                // This test will call via the RPC proxy to find a party of another node to verify that the nodes have
                // started and can communicate. This is a very basic test, in practice tests would be starting flows,
                // and verifying the states in the vault and other important metrics to ensure that your CorDapp is working
                // as intended.
                assertEquals(partyAHandle.getRpc().wellKnownPartyFromX500Name(partyB.getName()).getName(), partyB.getName());
                assertEquals(partyBHandle.getRpc().wellKnownPartyFromX500Name(partyA.getName()).getName(), partyA.getName());
                assertEquals(partyAHandle.getRpc().wellKnownPartyFromX500Name(partyC.getName()).getName(), partyC.getName());
                assertEquals(partyBHandle.getRpc().wellKnownPartyFromX500Name(partyC.getName()).getName(), partyC.getName());
                assertEquals(partyCHandle.getRpc().wellKnownPartyFromX500Name(partyB.getName()).getName(), partyB.getName());
                assertEquals(partyCHandle.getRpc().wellKnownPartyFromX500Name(partyA.getName()).getName(), partyA.getName());

                // Start CreateFlow and VaultQuery for AuctionState in initiator(PartyB) vault
                partyAHandle.getRpc().startFlowDynamic(RequestFlow.class, partyB.getParty(), "test", "Funding for Testing", (long) 1000).getReturnValue().get();
                String output = partyBHandle.getRpc().vaultQuery(TransferState.class).getStates().get(0).component1().getData().getUid();
                partyBHandle.getRpc().startFlowDynamic(ApproveFlow.class, output, 1000).getReturnValue().get();
//                partyBHandle.getRpc().startFlowDynamic(BidFlow.class, 99, "test").getReturnValue().get();
//
//                partyCHandle.getRpc().startFlowDynamic(EntryFlow.class, "test").getReturnValue().get();
//                partyCHandle.getRpc().startFlowDynamic(BidFlow.class, 50, "test").getReturnValue().get();
//
//                assertEquals(1, partyAHandle.getRpc().vaultQuery(AuctionState.class).getStates().size());
//                AuctionState output = partyBHandle.getRpc().vaultQuery(AuctionState.class).getStates().get(0).component1().getData();
//
//                assertEquals("test", output.getName());
//                assertEquals(50, (int) output.getValue());

            } catch (Exception e) {
                throw new RuntimeException("Caught exception during test", e);
            }

            return null;
        });
    }
}