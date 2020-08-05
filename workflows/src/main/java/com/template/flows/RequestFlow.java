package com.template.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.template.contracts.TemplateContract;
import com.template.states.TransferState;
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
public class RequestFlow extends FlowLogic<SignedTransaction> {
    private final Party issuer;
    private final long amount;
    private final String title;
    private final String description;

    /**
     * The progress tracker provides checkpoints indicating the progress of the flow to observers.
     */
    private final ProgressTracker progressTracker = new ProgressTracker();

    public RequestFlow(Party issuer, String title, String description, long amount) {
        this.issuer = issuer;
        this.amount = amount;
        this.description = description;
        this.title = title;
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

        // Get all nodes without notary to be added to the Transfer constructor as AllNodes
        List<AbstractParty> parties = getServiceHub().getNetworkMapCache().getAllNodes().stream()
                .map(nodeInfo -> nodeInfo.getLegalIdentities().get(0))
                .collect(Collectors.toList());
        parties.remove(notary);

        // We create the transaction components.

        // Create outputState whose time of creation is now and expires in 30days
        Instant now = Instant.now();
        String uid = Utils.sha1(issuer.getOwningKey().toString(), getOurIdentity().getOwningKey().toString(), Long.toString(amount), now.toString(), title, description);
        TransferState outputState = new TransferState(issuer, getOurIdentity(), parties, amount, now, now.plusSeconds(86400*30), false, uid, title, description);

        // Put all signers PubicKey into a list
        List<PublicKey> signers = new ArrayList<PublicKey>();
        signers.add(getOurIdentity().getOwningKey());
        signers.add(issuer.getOwningKey());

        // Create Command from CommandData Bid and list of required signers
        Command command = new Command<>(new TemplateContract.Commands.Request(), signers);
        // We create a transaction builder and add the components.
        TransactionBuilder txBuilder = new TransactionBuilder(notary)
                .addOutputState(outputState, TemplateContract.ID)
                .addCommand(command);

        // Verify transaction
        txBuilder.verify(getServiceHub());

        // Self Signing the transaction.
        SignedTransaction signedTx = getServiceHub().signInitialTransaction(txBuilder);

        // Create a Session with the issuer and initiate CollectSignaturesFlow
        FlowSession issuerSes = initiateFlow(issuer);
        issuerSes.send(true);
        signedTx = subFlow(new CollectSignaturesFlow(signedTx, Collections.singletonList(issuerSes)));

        // Initiate Session with issuer to Finalize flow
        List<FlowSession> allSessions = new ArrayList<FlowSession>();
        allSessions.add(issuerSes);
//
//        for(AbstractParty party: parties){
//            if(!party.equals(getOurIdentity())) {
//                FlowSession session = initiateFlow(party);
//                session.send(false);
//                allSessions.add(session);
//            }
//        }


        return subFlow(new FinalityFlow(signedTx, allSessions));
    }
}