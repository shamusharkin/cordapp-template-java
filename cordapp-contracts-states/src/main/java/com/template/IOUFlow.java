package com.template;

//import co.paralleluniverse.fibers.Suspendable;
//import net.corda.core.contracts.Command;
//import net.corda.core.contracts.CommandData;
//import net.corda.core.flows.*;
//import net.corda.core.identity.Party;
//import net.corda.core.transactions.SignedTransaction;
//import net.corda.core.transactions.TransactionBuilder;
//import net.corda.core.utilities.ProgressTracker;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.StateAndContract;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;

import java.security.PublicKey;
import java.util.List;
//import static net.corda.docs.java.tutorial.helloworld.IOUContract.TEMPLATE_CONTRACT_ID;
//import com.template.IOUContract;

//initiating flow means that this flow can be started directly by a node
@InitiatingFlow
//startablebyrpc allows the node owner to start this flow via an RPC call
@StartableByRPC
public class IOUFlow extends FlowLogic<Void> {
    private final Integer iouValue;
    private final Party otherParty;

    /**
     * The progress tracker provides checkpoints indicating the progress of the flow to observers.
     */
    private final ProgressTracker progressTracker = new ProgressTracker();

    public IOUFlow(Integer iouValue, Party otherParty) {
        this.iouValue = iouValue;
        this.otherParty = otherParty;
    }

    @Override
    public ProgressTracker getProgressTracker() {
        return progressTracker;
    }

    /**
     * The flow logic is encapsulated within the call() method.
     */
    //suspendable - allows the flow to be check-pointed and serialised to disk when it encounters a long-running operation, allowing your node to move on to running other flows
    @Suspendable
    @Override
    public Void call() throws FlowException {
        //ServiceHub - Whenever we need information within a flow - whether it’s about our own node’s identity, the node’s local storage, or the rest of the network - we generally obtain it via the node’s ServiceHub.
        //networkMapCache - provides information about the other nodes on the network and the services that they offer
        final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

        // We create a transaction builder and add the components.
        final TransactionBuilder txBuilder = new TransactionBuilder(notary);

        // We create the transaction components.
        IOUState outputState = new IOUState(iouValue, getOurIdentity(), otherParty);
        String outputContract = IOUContract.class.getName();
        //pass in a new instance of the IOU state and a copy of the contract class to create an output state
        StateAndContract outputContractAndState = new StateAndContract(outputState, outputContract);
        //get a list of signers, ie lender and borrower
        List<PublicKey> requiredSigners = ImmutableList.of(getOurIdentity().getOwningKey(), otherParty.getOwningKey());
        //add a create action and a list of 2 required signers to the cmd, lender & borrower
        Command cmd = new Command<>(new IOUContract.Create(), requiredSigners);

        // We add the contract/state and create command/req signers to the tx builder.
        txBuilder.withItems(outputContractAndState, cmd);

        // Verifying the transaction.
        txBuilder.verify(getServiceHub());

        // Signing the transaction.
        final SignedTransaction signedTx = getServiceHub().signInitialTransaction(txBuilder);

        // Creating a session with the borrower to request their signature over the transaction
        //If the counterparty (otherParty) has a FlowLogic registered to respond to the FlowLogic initiating the session, a session will be established
        FlowSession otherpartySession = initiateFlow(otherParty);

        // Obtaining the counterparty's signature.  once we have a flowsession established, we get the borrowers sig using CollectSignaturesFlow
        //subFlow takes a fully signed transaction and a list of flow-sessions between the flow initiator and the required signers
        //returns a transaction signed by all the required signers
        //this initiates IOUFow responder!
        SignedTransaction fullySignedTx = subFlow(new CollectSignaturesFlow(
                signedTx, ImmutableList.of(otherpartySession), CollectSignaturesFlow.tracker()));

        // we pass the fully signed transaction (fullySignedTx) into FinalityFlow
        subFlow(new FinalityFlow(fullySignedTx));

        return null;
    }
}