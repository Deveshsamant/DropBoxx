package com.dropnest

import com.dropnest.model.AccessDecision
import com.dropnest.model.MessageStatus
import com.dropnest.model.Peer
import com.dropnest.platform.DesktopFile
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Messages queue locally and are delivered only to devices that chose "Always allow" for us. */
class ChatIntegrationTest {

    private lateinit var root: File
    private lateinit var alice: TestDevice
    private lateinit var bob: TestDevice
    private lateinit var bobPeer: Peer

    @BeforeTest
    fun setUp() = runBlocking<Unit> {
        root = Files.createTempDirectory("dropnest-chat").toFile()
        alice = TestDevice("Alice", root); bob = TestDevice("Bob", root)
        alice.start()
        bobPeer = assertNotNull(alice.discovery.addManual("127.0.0.1", bob.start()))
    }

    @AfterTest
    fun tearDown() { alice.stop(); bob.stop(); root.deleteRecursively() }

    @Test
    fun untrustedMessageIsRefusedThenDeliveredOnceTrusted() = runBlocking<Unit> {
        // Bob has not trusted Alice: her message must fail clearly, not prompt Bob.
        assertTrue(!alice.chat.canMessage(bobPeer.id))
        alice.chat.send(bobPeer.info, "hello?")
        val failed = withTimeout(30_000) { alice.chat.messages.first { l -> l.any { it.status == MessageStatus.FAILED } } }.first()
        assertEquals(MessageStatus.FAILED, failed.status)
        assertTrue(bob.chat.messages.value.isEmpty())
        assertEquals(null, bob.boxAccess.accessRequest.value)

        // Alice opens Bob's box; Bob answers "always allow" -> Alice gets a pair token -> chat works.
        val browse = launch { alice.engine.browse(bobPeer) }
        val req = withTimeout(30_000) { bob.boxAccess.accessRequest.filterNotNull().first() }
        bob.boxAccess.respond(req.id, AccessDecision(allow = true, always = true))
        browse.join()
        assertTrue(alice.chat.canMessage(bobPeer.id))

        alice.chat.retry(failed.id)
        alice.chat.send(bobPeer.info, "now it works")
        val delivered = withTimeout(30_000) { alice.chat.messages.first { l -> l.size == 2 && l.all { it.status == MessageStatus.SENT } } }
        assertEquals(listOf("hello?", "now it works"), delivered.sortedBy { it.sentAt }.map { it.text })
        val onBob = withTimeout(30_000) { bob.chat.messages.first { it.size == 2 } }
        assertTrue(onBob.all { !it.fromMe && !it.read && it.peerId == alice.identity.info.value.id })
        assertEquals(1, bob.chat.conversations.value.size)
        assertEquals(2, bob.chat.conversations.value.first().unread)
        assertTrue(bob.notifications.any { it.startsWith("Alice |") })

        // Reply the other way needs Alice to trust Bob too.
        bob.chat.send(alice.identity.info.value, "hi back")
        val bobFailed = withTimeout(30_000) { bob.chat.messages.first { l -> l.any { it.fromMe && it.status == MessageStatus.FAILED } } }
        assertTrue(bobFailed.any { it.fromMe && it.status == MessageStatus.FAILED })

        // Private drop: Alice sends Bob a file through the chat. It sits in Alice's box for Bob only.
        val secret = File(root, "for-bob.bin").also { it.writeBytes(ByteArray(200_000) { it.toByte() }) }
        assertTrue(alice.chat.sendFiles(bobPeer.info, listOf(DesktopFile(secret))).isEmpty())
        val fileMsg = withTimeout(30_000) { bob.chat.messages.first { l -> l.any { it.attachment != null } } }.first { it.attachment != null }
        assertEquals("for-bob.bin", fileMsg.attachment!!.name)
        val privateItem = alice.box.items.value.first { it.forPeerId == bobPeer.id }
        assertEquals("Bob", privateItem.forPeerAlias)
        // Bob (trusted by Alice? not yet) - Alice must trust Bob for Bob to browse Alice's box.
        val alicePeer = assertNotNull(bob.discovery.peers.value.firstOrNull { it.id == alice.identity.info.value.id })
        val browse2 = launch { bob.engine.browse(alicePeer) }
        val req2 = withTimeout(30_000) { alice.boxAccess.accessRequest.filterNotNull().first() }
        alice.boxAccess.respond(req2.id, AccessDecision(allow = true, always = true))
        browse2.join()
        val listing = bob.engine.browse(alicePeer)
        assertTrue(listing is com.dropnest.domain.BrowseOutcome.Ok && listing.items.any { it.id == privateItem.id })
        val sid = bob.engine.download(alicePeer, (listing as com.dropnest.domain.BrowseOutcome.Ok).items.filter { it.id == privateItem.id }, listing.accessToken)
        val done = withTimeout(60_000) { bob.engine.sessions.first { l -> l.any { it.id == sid && it.status.isTerminal } }.first { it.id == sid } }
        assertEquals(com.dropnest.model.SessionStatus.COMPLETED, done.status)
        // A third device Alice also trusts must not see the private item.
        val carol = TestDevice("Carol", root); carol.start()
        val aliceForCarol = assertNotNull(carol.discovery.addManual("127.0.0.1", alice.identity.info.value.port))
        val browse3 = launch { carol.engine.browse(aliceForCarol) }
        val req3 = withTimeout(30_000) { alice.boxAccess.accessRequest.filterNotNull().first() }
        alice.boxAccess.respond(req3.id, AccessDecision(allow = true, always = true))
        browse3.join()
        val carolSees = carol.engine.browse(aliceForCarol) as com.dropnest.domain.BrowseOutcome.Ok
        assertTrue(carolSees.items.none { it.id == privateItem.id })
        carol.stop()

        // Persistence: a fresh service over Bob's directory still has the thread.
        val reloaded = com.dropnest.engine.chat.ChatServiceImpl(bob.dir.absolutePath, bob.identity, bob.trust, bob.discovery, bob.clients, bob.platform, bob.box, bob.scope)
        assertEquals(4, reloaded.messages.value.size)
    }
}
