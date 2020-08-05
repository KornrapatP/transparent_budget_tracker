package com.template.contracts;

import com.template.states.TransferState;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.Contract;
import net.corda.core.contracts.TransactionState;
import net.corda.core.transactions.LedgerTransaction;

import java.time.Instant;

// ************
// * Contract *
// ************
public class TemplateContract implements Contract {
    // This is used to identify our contract when building a transaction.
    public static final String ID = "com.template.contracts.TemplateContract";

    // A transaction is valid if the verify() function of the contract of all the transaction's input and output states
    // does not throw an exception.
    @Override
    public void verify(LedgerTransaction tx) {
        if(tx.getCommands().size() != 1){
            throw new IllegalArgumentException("One command Expected");
        }

        Command command = tx.getCommand(0);
        if (command.getValue() instanceof Commands.Request) {
            verifyRequest(tx);
        } else if (command.getValue() instanceof Commands.Approve) {
            verifyApprove(tx);
        } else if (command.getValue() instanceof  Commands.Decline) {
            verifyDecline(tx);
        }
    }

    private void verifyRequest(LedgerTransaction tx) {
        if(tx.getInputStates().size() != 0) throw new IllegalArgumentException("Zero Input Expected");
        if(tx.getOutputStates().size() != 1) throw new IllegalArgumentException("One Output Expected");
        TransferState output = (TransferState) tx.getOutput(0);
        Command command = tx.getCommand(0);
        if (!command.getSigners().contains(output.getIssuer().getOwningKey()) || !command.getSigners().contains(output.getRequester().getOwningKey())) throw new IllegalArgumentException("Signers not present in the command!");
        if (command.getSigners().size()!=2) throw new IllegalArgumentException("Signers in command should be 2!");

        if (!(output.getValidUntil().isAfter(Instant.now()) && output.getRequestDate().isBefore(Instant.now()))) throw new IllegalArgumentException("TimeWindow not valid!");
        if (output.getApprove()) throw new IllegalArgumentException("Requester cannot approve this transfer on their own!");
        if (output.getAmount()<=0) throw new IllegalArgumentException("Invalid request value!");
        if (output.getTitle().isEmpty() || output.getDescription().isEmpty()) throw new IllegalArgumentException("Title and Description cannot be empty!");

    }

    private void verifyApprove(LedgerTransaction tx) {
        if(tx.getInputStates().size() != 1) throw new IllegalArgumentException("One Input Expected");
        if(tx.getOutputStates().size() != 1) throw new IllegalArgumentException("One Output Expected");
        TransferState output = (TransferState) tx.getOutput(0);
        TransferState input = (TransferState) tx.getInput(0);
        Command command = tx.getCommand(0);
        if (!command.getSigners().contains(output.getIssuer().getOwningKey()) || !command.getSigners().contains(output.getRequester().getOwningKey())) throw new IllegalArgumentException("Signers not present in the command!");
        if (command.getSigners().size()!=2) throw new IllegalArgumentException("Signers in command should be 2!");
        if (!input.getTitle().equals(output.getTitle())) throw new IllegalArgumentException("Title does not match!");
        if (!input.getDescription().equals(output.getDescription())) throw new IllegalArgumentException("Description does not match!");
        if (!input.getRequestDate().equals(output.getRequestDate()) || !output.getValidUntil().equals(Instant.MAX)) throw new IllegalArgumentException("Time issued and Time valid do not match!");
        if (!input.getIssuer().equals(output.getIssuer())) throw new IllegalArgumentException("Issuer does not match!");
        if (!input.getRequester().equals(output.getRequester())) throw new IllegalArgumentException("Requester does not match!");
        if (input.getAmount() < output.getAmount()) throw new IllegalArgumentException("Cannot approve more than requested");
        if (output.getAmount()<=0) throw new IllegalArgumentException("Cannot approve non-positive value!");
        if (!(output.getAllNodes().containsAll(input.getAllNodes()) && input.getAllNodes().containsAll(output.getAllNodes()))) throw new IllegalArgumentException("All nodes must remain the same.");
        if (input.getApprove() || !output.getApprove()) throw new IllegalArgumentException("Approve values invalid");
        if (!(input.getValidUntil().isAfter(Instant.now()) && input.getRequestDate().isBefore(Instant.now()))) throw new IllegalArgumentException("TimeWindow not valid! Please decline the request.");
    }

    private void verifyDecline(LedgerTransaction tx) {
        if(tx.getInputStates().size() != 1) throw new IllegalArgumentException("One Input Expected");
        if(tx.getOutputStates().size() != 0) throw new IllegalArgumentException("Zero Output Expected");
        TransferState input = (TransferState) tx.getInput(0);
        Command command = tx.getCommand(0);
        if (!command.getSigners().contains(input.getIssuer().getOwningKey())) throw new IllegalArgumentException("Signers not present in the command!");
        if (command.getSigners().size()!=1) throw new IllegalArgumentException("Signers in command should be 2!");
        if (input.getApprove()) throw new IllegalArgumentException("Cannot decline approved states!");

    }

    // Used to indicate the transaction's intent.
    public interface Commands extends CommandData {
        class Request implements Commands {}
        class Approve implements Commands {}
        class Decline implements Commands {}
    }
}