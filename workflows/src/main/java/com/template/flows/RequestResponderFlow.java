package com.template.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.template.states.TransferState;
import net.corda.core.flows.*;
import net.corda.core.transactions.SignedTransaction;
import org.jetbrains.annotations.NotNull;

// ******************
// * Responder flow *
// ******************
@InitiatedBy(RequestFlow.class)
public class RequestResponderFlow extends FlowLogic<SignedTransaction> {
    private final FlowSession otherPartySession;

    public RequestResponderFlow(FlowSession otherPartySession) {
        this.otherPartySession = otherPartySession;
    }

    @Suspendable
    @Override
    public SignedTransaction call() throws FlowException {
        boolean flag = otherPartySession.receive(Boolean.class).unwrap(it -> it);
        // Flag to decide when CollectSignaturesFlow is called for this counterparty. SignTransactionFlow is
        // executed only if CollectSignaturesFlow is called from the initiator.
        if(flag) {
            subFlow(new SignTransactionFlow(otherPartySession) {
                @Override
                protected void checkTransaction(@NotNull SignedTransaction stx) throws FlowException {
                    TransferState out = (TransferState) stx.getCoreTransaction().getOutput(0);
                    if (out.getApprove() || !out.getIssuer().equals(getOurIdentity())) {
                        throw new FlowException("Transfer must not be approved yet!");
                    }
                }
            });
        }
        return subFlow(new ReceiveFinalityFlow(otherPartySession));
    }
}