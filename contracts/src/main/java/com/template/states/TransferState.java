package com.template.states;

import com.template.contracts.TemplateContract;
import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.ContractState;
import net.corda.core.identity.AbstractParty;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

// *********
// * State *
// *********
@BelongsToContract(TemplateContract.class)
public class TransferState implements ContractState {

    private final String Uid;
    /** Issuer of the money */
    private final AbstractParty Issuer;

    /** Requester of the money */
    private final AbstractParty Requester;

    /** All nodes that is notified of the Creation of this state */
    private final List<AbstractParty> AllNodes;

    /** Amount Paid */
    private final long Amount;

    /** Request Date */
    private final Instant RequestDate;

    /** Infinite if Approve, else : instant that mark the expire status of the state */
    private final Instant ValidUntil;

    /** Has been approved by the issuer? */
    private final boolean Approve;

    private final String Title;

    private final String Description;

    /** Constructor */
    public TransferState(AbstractParty issuer, AbstractParty requester, List<AbstractParty> allNodes, long amount, Instant requestDate, Instant validUntil, boolean approve, String uid, String title, String description) {
        Uid = uid;
        Issuer = issuer;
        Requester = requester;
        AllNodes = allNodes;
        Amount = amount;
        RequestDate = requestDate;
        ValidUntil = validUntil;
        Approve = approve;
        Title = title;
        Description = description;
    }

    /** Issuer getter */
    public AbstractParty getIssuer() {
        return Issuer;
    }

    /** Requester getter */
    public AbstractParty getRequester() {
        return Requester;
    }

    /** RequestDate getter */
    public Instant getRequestDate() {
        return RequestDate;
    }

    /** ValidUntil getter */
    public Instant getValidUntil() {
        return ValidUntil;
    }

    /** AllNodes present at the creation of this state getter */
    public List<AbstractParty> getAllNodes() {
        return AllNodes;
    }

    /** Amount getter */
    public long getAmount() {
        return Amount;
    }

    /** Approved status getter */
    public boolean getApprove() {
        return Approve;
    }

    public String getUid() {
        return Uid;
    }

    public String getDescription() {
        return Description;
    }

    public String getTitle() {
        return Title;
    }

    @Override
    public List<AbstractParty> getParticipants() {
        return Approve ? AllNodes : Arrays.asList(Issuer, Requester);
    }
}