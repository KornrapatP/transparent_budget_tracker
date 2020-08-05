package com.template.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.template.contracts.TemplateContract;
import com.template.states.TransferState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.contracts.Command;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;

import java.security.PublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

// ******************
// * Initiator flow *
// ******************
@InitiatingFlow
@StartableByRPC
public class ApproveFlow extends FlowLogic<SignedTransaction> {
    private final String UID;
    private final long Amount;

    /**
     * The progress tracker provides checkpoints indicating the progress of the flow to observers.
     */
    private final ProgressTracker progressTracker = new ProgressTracker();

    public ApproveFlow(String uid, long amount) {
        this.UID = uid;
        this.Amount = amount;
    }

    @Override
    public ProgressTracker getProgressTracker() {
        return progressTracker;
    }

    /**
     * The flow logic is encapsulated within the call() method.
     */
    @Suspendable
    @Override
    public SignedTransaction call() throws FlowException {
        // We retrieve the notary identity from the network map.
        Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
        // We create the transaction components.

        // Query the vault to fetch a list of all AuctionState state, and filter the results based on the auctionName
        // to fetch the desired AuctionState state from the vault. This filtered state would be used as input to the
        // transaction.
        List<StateAndRef<TransferState>> transferStateAndRefs = getServiceHub().getVaultService()
                .queryBy(TransferState.class).getStates();
        StateAndRef<TransferState> inputStateAndRef = transferStateAndRefs.stream().filter(transferStateAndRef -> {
            TransferState transferState = transferStateAndRef.getState().getData();
            return transferState.getUid().startsWith(UID) && !transferState.getApprove() && transferState.getIssuer().equals(getOurIdentity());
        }).findAny().orElseThrow(() -> new IllegalArgumentException("Transfer request Not Found"));


        TransferState input = inputStateAndRef.getState().getData();
        // Put all signers PubicKey into a list
        List<PublicKey> signers = new ArrayList<PublicKey>();
        signers.add(getOurIdentity().getOwningKey());
        signers.add(input.getRequester().getOwningKey());
        // Create Command from CommandData Bid and list of required signers
        Command command = new Command<>(new TemplateContract.Commands.Approve(), signers);
        //Create output state
        TransferState output = new TransferState(getOurIdentity(), input.getRequester(), input.getAllNodes(), Amount, input.getRequestDate(), Instant.MAX, true, input.getUid(), input.getTitle(), input.getDescription());
        // We create a transaction builder and add the components.
        TransactionBuilder txBuilder = new TransactionBuilder(notary)
                .addOutputState(output)
                .addInputState(inputStateAndRef)
                .addCommand(command);
        // Verify transaction
        txBuilder.verify(getServiceHub());
        // Self Signing the transaction.
        SignedTransaction signedTx = getServiceHub().signInitialTransaction(txBuilder);
        FlowSession requesterSes = initiateFlow(input.getRequester());
        requesterSes.send(true);
        signedTx = subFlow(new CollectSignaturesFlow(signedTx, Collections.singletonList(requesterSes)));
        // Initiate Session with issuer to Finalize flow
        List<FlowSession> allSessions = new ArrayList<FlowSession>();
        allSessions.add(requesterSes);
        List<AbstractParty> parties = getServiceHub().getNetworkMapCache().getAllNodes().stream()
                .map(nodeInfo -> nodeInfo.getLegalIdentities().get(0))
                .collect(Collectors.toList());
        parties.remove(notary);
        for(AbstractParty party: parties){
            if(!party.equals(getOurIdentity())) {
                FlowSession session = initiateFlow(party);
                session.send(false);
                allSessions.add(session);
            }
        }

        return subFlow(new FinalityFlow(signedTx, allSessions));
    }
}