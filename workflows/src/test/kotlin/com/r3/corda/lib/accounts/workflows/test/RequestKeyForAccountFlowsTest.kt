package com.r3.corda.lib.accounts.workflows.test

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount
import com.r3.corda.lib.accounts.workflows.internal.accountService
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowException
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RequestKeyForAccountFlowsTest {

    lateinit var network: MockNetwork
    lateinit var nodeA: StartedMockNode
    lateinit var nodeB: StartedMockNode

    @Before
    fun setup() {
        network = MockNetwork(
                MockNetworkParameters(
                        networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
                        cordappsForAllNodes = listOf(
                                TestCordapp.findCordapp("com.r3.corda.lib.accounts.contracts"),
                                TestCordapp.findCordapp("com.r3.corda.lib.accounts.workflows")
                        )
                )
        )
        nodeA = network.createPartyNode()
        nodeB = network.createPartyNode()

        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    @Test
    fun `should create locally when request is local`() {
        val accountA = nodeA.startFlow(CreateAccount("Test_AccountA")).runAndGet(network)
        val confidentIdentityA = nodeA.startFlow(RequestKeyForAccount(accountA.state.data)).runAndGet(network)
        assertNotNull(confidentIdentityA)
        val accountServiceA = nodeA.services.accountService
        nodeA.transaction {
            //check if the the key was actually generated by node nodeA
            nodeA.services.identityService.requireWellKnownPartyFromAnonymous(confidentIdentityA)
            val keysForAccountA = accountServiceA.accountKeys(accountA.state.data.identifier.id)
            assertEquals(keysForAccountA, listOf(confidentIdentityA.owningKey))
        }
        nodeB.transaction {
            assertNull(nodeB.services.identityService.partyFromKey(confidentIdentityA.owningKey))
        }
    }

    //checks if the flow returns nodeA public key for the account
    @Test
    fun `should return key when a node requests`() {
        val accountA = nodeA.startFlow(CreateAccount("Test_AccountA")).runAndGet(network)
        // nodeB requesting key for account
        val confidentIdentityA = nodeB.startFlow(RequestKeyForAccount(accountA.state.data)).runAndGet(network)
        val accountServiceA = nodeA.services.accountService
        //verify that nodeA key is returned
        assertNotNull(confidentIdentityA)
        nodeB.transaction {
            //check if the the key was actually generated by node nodeA
            nodeB.services.identityService.requireWellKnownPartyFromAnonymous(confidentIdentityA)
        }
        val keysForAccountA = nodeA.transaction {
            accountServiceA.accountKeys(accountA.state.data.identifier.id)
        }
        assertEquals(keysForAccountA, listOf(confidentIdentityA.owningKey))
    }

    //check if it is possible to access account using the public key generated
    @Test
    fun `should be possible to get the account by newly created key`() {
        val accountA = nodeA.startFlow(CreateAccount("Test_AccountA")).runAndGet(network)
        //nodeB request public key
        val confidentIdentityA = nodeB.startFlow((RequestKeyForAccount(accountA.state.data))).runAndGet(network)
        val accountService = nodeA.services.accountService
        nodeA.transaction {
            //access the account using accountInfo method ,passing the Public key as parameter
            // and check if the account returned is 'accountA'.
            assertEquals(accountService.accountInfo(confidentIdentityA.owningKey), accountA)
        }
    }

    @Test(expected = FlowException::class)
    fun `should throw if remote node does not have account`() {
        val accountA = AccountInfo("Test_Account", nodeA.identity(), UniqueIdentifier())
        //nodeB request public key
        nodeB.startFlow((RequestKeyForAccount(accountA))).runAndGet(network)
    }

}
