package com.template.flows;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.flows.*;
import net.corda.core.transactions.SignedTransaction;


// ******************
// * Responder flow *
// ******************
@InitiatedBy(DeclineFlow.class)
public class DeclineResponderFlow extends FlowLogic<SignedTransaction> {
    private final FlowSession otherPartySession;

    public DeclineResponderFlow(FlowSession otherPartySession) {
        this.otherPartySession = otherPartySession;
    }

    @Suspendable
    @Override
    public SignedTransaction call() throws FlowException {
        // Finalize the Flow and save new state
        return subFlow(new ReceiveFinalityFlow(otherPartySession));
    }
}