package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.utilities.ProgressTracker
import net.corda.core.flows.FinalityFlow
import net.corda.core.contracts.Command
import com.template.contracts.IOUContract
import com.template.states.IOUState
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.contracts.requireThat
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction

// *********
// * Flows *
// *********
@InitiatingFlow
@StartableByRPC
class IOUFlow(val iouValue: Int,
              val otherParty: Party
) : FlowLogic<Unit>() {

    /** The progress tracker provides checkpoints indicating the progress of the flow to observers. */
    override val progressTracker = ProgressTracker()

    /** The flow logic is encapsulated within the call() method. */
    @Suspendable
    override fun call() {
        // You retrieve the notary identity from the network map.
        val notary = serviceHub.networkMapCache.notaryIdentities[0]

// You create the transaction components.
        val outputState = IOUState(iouValue, ourIdentity, otherParty)
        val command = Command(IOUContract.Create(), listOf(ourIdentity.owningKey, otherParty.owningKey))

// You create a transaction builder and add the components.
        val txBuilder = TransactionBuilder(notary = notary)
            .addOutputState(outputState, IOUContract.ID)
            .addCommand(command)

// Verifying the transaction.
        txBuilder.verify(serviceHub)

// Signing the transaction.
        val signedTx = serviceHub.signInitialTransaction(txBuilder)

// Creating a session with the other party.
        val otherPartySession = initiateFlow(otherParty)

// Obtaining the counterparty's signature.
        val fullySignedTx = subFlow(CollectSignaturesFlow(signedTx, listOf(otherPartySession), CollectSignaturesFlow.tracker()))

// Finalising the transaction.
        subFlow(FinalityFlow(fullySignedTx, otherPartySession))
    }
}
@InitiatedBy(IOUFlow::class)
class

der(val otherPartySession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val signTransactionFlow = object : SignTransactionFlow(otherPartySession, SignTransactionFlow.tracker()) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val output = stx.tx.outputs.single().data
                "This must be an IOU transaction." using (output is IOUState)
                val iou = output as IOUState
                "The IOU's value can't be too high." using (iou.value < 100)
            }
        }
        subFlow(signTransactionFlow)
        subFlow(ReceiveFinalityFlow(otherPartySession))
        return
    }
}