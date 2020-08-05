package com.template.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.template.contracts.TemplateContract;
import com.template.states.TransferState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.flows.*;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.contracts.Command;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

// ******************
// * Initiator flow *
// ******************
@InitiatingFlow
@StartableByRPC
public class DeclineFlow extends FlowLogic<SignedTransaction> {
    private final String UID;

    /**
     * The progress tracker provides checkpoints indicating the progress of the flow to observers.
     */
    private final ProgressTracker progressTracker = new ProgressTracker();

    public DeclineFlow(String uid) {
        this.UID = uid;
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

        // Create Command from CommandData Bid and list of required signers
        Command command = new Command<>(new TemplateContract.Commands.Decline(), signers);

        // We create a transaction builder and add the components.
        TransactionBuilder txBuilder = new TransactionBuilder(notary)
                .addInputState(inputStateAndRef)
                .addCommand(command);

        // Verify transaction
        txBuilder.verify(getServiceHub());

        // Self Signing the transaction.
        SignedTransaction signedTx = getServiceHub().signInitialTransaction(txBuilder);

        // Create a Session with the Auctioneer and initiate CollectSignaturesFlow
        FlowSession requesterSes = initiateFlow(input.getRequester());

        // Initiate Sessions with all participants to Finalize flow
        List<FlowSession> allSessions = new ArrayList<FlowSession>();
        allSessions.add(requesterSes);

        return subFlow(new FinalityFlow(signedTx, allSessions));
    }
}