package com.template;

import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.CommandWithParties;
import net.corda.core.contracts.Contract;
import net.corda.core.identity.Party;
import net.corda.core.transactions.LedgerTransaction;

import java.security.PublicKey;
import java.util.List;

import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;
import static net.corda.core.contracts.ContractsDSL.requireThat;

public class IOUContract implements Contract {

    // Our Create command. cmds do 2 things. the operation ie. create and has the required signers
    public static class Create implements CommandData {
    }

    //anything in the verify function is the actual contract
    //the verify function only has access to the contents of the transaction passed in as tx
    //the verify function does not throw an exception
    @Override
    public void verify(LedgerTransaction tx) {
        System.out.println("IOUContract.Create.class"+IOUContract.Create.class);
        System.out.println("tx.getCommands()"+ tx.getCommands());

        //we require that there should be one output state created.
        //requireSingleCommand ensures that a transaction has only one command that is of the given type, otherwise throws an exception
        //so we pass in the tx commands, and the type of command we are expecting.  there needs to be one create command else an exception is thrown
        final CommandWithParties<IOUContract.Create> command = requireSingleCommand(tx.getCommands(), IOUContract.Create.class);

        //transaction constraints within the requireThat method
        requireThat(check -> {
            // Constraints on the shape of the transaction.  what does $recevier mean?
            //If the condition on the right-hand side doesn’t evaluate to true...hrow an IllegalArgumentException with the message on the left-hand side
            check.using("No inputs should be consumed when issuing an IOU.", tx.getInputs().isEmpty());
            check.using("There should be one output state of type IOUState.", tx.getOutputs().size() == 1);

            // IOU-specific constraints.
            //extracting the transaction’s single IOUState and assigning it to a variable
            final IOUState out = tx.outputsOfType(IOUState.class).get(0);
            final Party lender = out.getLender();
            final Party borrower = out.getBorrower();
            check.using("The IOU's value must be non-negative.", out.getValue() > 0);
            check.using("The lender and the borrower cannot be the same entity.", lender != borrower);

            // Constraints on the signers.
            //A transaction’s required signers is equal to the union of all the signers listed on the commands
            //it ensures that no IOUState can ever be created on the ledger without the express agreement of both the lender and borrower nodes.
            final List<PublicKey> signers = command.getSigners();
            check.using("There must be two signers.", signers.size() == 2);
            check.using("The borrower and lender must be signers.", signers.containsAll(
                    ImmutableList.of(borrower.getOwningKey(), lender.getOwningKey())));

            return null;
        });
    }
}