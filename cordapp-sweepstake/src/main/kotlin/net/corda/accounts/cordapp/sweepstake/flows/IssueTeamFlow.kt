package net.corda.accounts.cordapp.sweepstake.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.accounts.states.AccountInfo
import net.corda.accounts.cordapp.sweepstake.contracts.TournamentContract
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.TransactionBuilder
import net.corda.accounts.cordapp.sweepstake.states.TeamState
import net.corda.accounts.flows.RequestKeyForAccountFlow

class IssueTeamFlow(
        private val accountInfo: StateAndRef<AccountInfo>,
        private val team: WorldCupTeam) : FlowLogic<StateAndRef<TeamState>>() {

    @Suspendable
    override fun call(): StateAndRef<TeamState> {
        val keyToUse = accountInfo.state.data.let {
            subFlow(RequestKeyForAccountFlow(accountInfo = it))
        }.owningKey
        val txBuilder = TransactionBuilder(notary = serviceHub.networkMapCache.notaryIdentities.first())
        txBuilder.addCommand(TournamentContract.ISSUE, serviceHub.myInfo.legalIdentities.first().owningKey)
        txBuilder.addOutputState(TeamState(team, true, keyToUse))
        val signedTxLocally = serviceHub.signInitialTransaction(txBuilder)
        val finalizedTx = subFlow(FinalityFlow(signedTxLocally, listOf()))
        return finalizedTx.coreTransaction.outRefsOfType(TeamState::class.java).single()
    }
}

