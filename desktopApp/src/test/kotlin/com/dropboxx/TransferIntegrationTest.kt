package com.dropboxx

import com.dropboxx.domain.SendOutcome
import com.dropboxx.model.IncomingDecision
import com.dropboxx.model.ItemKind
import com.dropboxx.model.OutgoingItem
import com.dropboxx.model.Peer
import com.dropboxx.model.SessionStatus
import com.dropboxx.model.TransferSession
import com.dropboxx.platform.DesktopFile
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Two engines in one JVM talking over real TLS on loopback. */
class TransferIntegrationTest {

    private lateinit var root: File
    private lateinit var alice: TestDevice
    private lateinit var bob: TestDevice
    private lateinit var bobAsSeenByAlice: Peer

    @BeforeTest
    fun setUp() = runBlocking<Unit> {
        root = Files.createTempDirectory("dropboxx-test").toFile()
        alice = TestDevice("Alice", root)
        bob = TestDevice("Bob", root)
        alice.start()
        val bobPort = bob.start()
        bobAsSeenByAlice = assertNotNull(alice.discovery.addManual("127.0.0.1", bobPort), "Bob should answer /info")
        assertEquals("Bob", bobAsSeenByAlice.info.alias)
    }

    @AfterTest
    fun tearDown() {
        alice.stop(); bob.stop()
        root.deleteRecursively()
    }

    private fun randomFile(name: String, size: Int): File =
        File(root, name).also { it.parentFile?.mkdirs(); it.writeBytes(Random.nextBytes(size)) }

    private fun sha256(f: File) = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }

    private suspend fun awaitSession(device: TestDevice, predicate: (TransferSession) -> Boolean): TransferSession =
        withTimeout(60_000) { device.engine.sessions.first { list -> list.any(predicate) }.first(predicate) }

    private fun acceptOnce(trust: Boolean) = bob.scope.launch {
        val req = bob.engine.incomingRequest.filterNotNull().first()
        bob.engine.respond(req.sessionId, IncomingDecision(accept = true, trustSender = trust))
    }

    @Test
    fun filesTextAndLinkArriveIntactAfterAcceptAndTrust() = runBlocking<Unit> {
        val big = randomFile("movie.bin", 6 * 1024 * 1024)
        val small = randomFile("note.txt", 1234)
        val items = listOf(
            OutgoingItem.File("f1", DesktopFile(big)),
            OutgoingItem.File("f2", DesktopFile(small)),
            OutgoingItem.Text("t1", "hello from alice"),
            OutgoingItem.Url("u1", "https://example.com/page"),
        )
        val received = async { bob.engine.receivedContent.take(2).toList() }

        // Bob accepts and ticks "always accept" while Alice's prepare request is pending.
        val decision = launch {
            val req = withTimeout(20_000) { bob.engine.incomingRequest.filterNotNull().first() }
            assertEquals("Alice", req.sender.alias)
            assertEquals(4, req.items.size)
            bob.engine.respond(req.sessionId, IncomingDecision(accept = true, trustSender = true))
        }
        assertEquals(SendOutcome.Started, alice.engine.send(bobAsSeenByAlice, items))
        decision.join()

        val sent = awaitSession(alice) { it.status.isTerminal }
        assertEquals(SessionStatus.COMPLETED, sent.status, sent.error)
        val got = awaitSession(bob) { it.status.isTerminal }
        assertEquals(SessionStatus.COMPLETED, got.status, got.error)

        val movie = File(bob.receivedDir, "movie.bin")
        assertTrue(movie.exists(), "movie.bin should land in Bob's receive folder")
        assertEquals(sha256(big), sha256(movie), "bytes must be identical")
        assertEquals(sha256(small), sha256(File(bob.receivedDir, "note.txt")))
        assertTrue(bob.receivedDir.listFiles()!!.none { it.name.endsWith(".part") }, "no leftover .part files")

        val texts = withTimeout(30_000) { received.await() }
        assertEquals(setOf(ItemKind.TEXT, ItemKind.URL), texts.map { it.kind }.toSet())
        assertEquals("hello from alice", texts.first { it.kind == ItemKind.TEXT }.content)

        // Trust was established in both directions: Bob stores Alice, Alice stores Bob's token.
        assertNotNull(bob.trust.find(alice.identity.info.value.id))
        assertNotNull(alice.trust.outgoingToken(bob.identity.info.value.id))
        assertEquals(1, bob.history.entries.value.size)
        assertTrue(bob.notifications.any { it.startsWith("Received") }, bob.notifications.toString())
    }

    @Test
    fun sameNameFilesInOneSessionStayIntact() = runBlocking<Unit> {
        acceptOnce(trust = false)
        val a = randomFile("a/photo.png", 900_000)
        val b = randomFile("b/photo.png", 700_000)
        val items = listOf(OutgoingItem.File("f1", DesktopFile(a)), OutgoingItem.File("f2", DesktopFile(b)))
        assertEquals(SendOutcome.Started, alice.engine.send(bobAsSeenByAlice, items))
        val done = awaitSession(alice) { it.status.isTerminal }
        assertEquals(SessionStatus.COMPLETED, done.status, done.error)
        val received = bob.receivedDir.listFiles().orEmpty().filter { it.name.startsWith("photo") }.sortedBy { it.name }
        assertEquals(listOf("photo (1).png", "photo.png"), received.map { it.name })
        assertEquals(setOf(sha256(a), sha256(b)), received.map { sha256(it) }.toSet(), "each file must be intact, not interleaved")
    }

    @Test
    fun trustedSenderIsAutoAcceptedWithoutDialog() = runBlocking<Unit> {
        acceptOnce(trust = true)
        alice.engine.send(bobAsSeenByAlice, listOf(OutgoingItem.Text("t", "first")))
        awaitSession(alice) { it.status.isTerminal }

        // Second transfer: Quick Save is on and Alice is trusted -> no incoming request is raised.
        val outcome = alice.engine.send(bobAsSeenByAlice, listOf(OutgoingItem.File("f", DesktopFile(randomFile("second.bin", 300_000)))))
        assertEquals(SendOutcome.Started, outcome)
        val done = awaitSession(alice) { it.status.isTerminal && it.items.any { i -> i.name == "second.bin" } }
        assertEquals(SessionStatus.COMPLETED, done.status, done.error)
        assertNull(bob.engine.incomingRequest.value)
        assertTrue(File(bob.receivedDir, "second.bin").exists())
    }

    @Test
    fun declinedTransferReportsDeclinedToSender() = runBlocking<Unit> {
        launch {
            val req = withTimeout(20_000) { bob.engine.incomingRequest.filterNotNull().first() }
            bob.engine.respond(req.sessionId, IncomingDecision(accept = false))
        }
        val outcome = alice.engine.send(bobAsSeenByAlice, listOf(OutgoingItem.Text("t", "nope")))
        assertEquals(SendOutcome.Declined, outcome)
        val s = awaitSession(alice) { it.status.isTerminal }
        assertEquals(SessionStatus.DECLINED, s.status)
        delay(200)
        assertNull(bob.engine.incomingRequest.value, "dialog state must be cleared after a decision")
    }

    @Test
    fun receiverPinIsEnforced() = runBlocking<Unit> {
        bob.settings.update { copy(pin = "4321") }
        assertEquals(SendOutcome.PinRequired, alice.engine.send(bobAsSeenByAlice, listOf(OutgoingItem.Text("t", "x"))))
        assertEquals(SendOutcome.PinRequired, alice.engine.send(bobAsSeenByAlice, listOf(OutgoingItem.Text("t", "x")), pin = "0000"))
        acceptOnce(trust = false)
        assertEquals(SendOutcome.Started, alice.engine.send(bobAsSeenByAlice, listOf(OutgoingItem.Text("t", "x")), pin = "4321"))
    }

    @Test
    fun senderCancelAbortsReceiverAndCleansUp() = runBlocking<Unit> {
        acceptOnce(trust = false)
        val huge = randomFile("huge.bin", 40 * 1024 * 1024)
        assertEquals(SendOutcome.Started, alice.engine.send(bobAsSeenByAlice, listOf(OutgoingItem.File("f", DesktopFile(huge)))))
        val active = awaitSession(alice) { it.status == SessionStatus.ACTIVE && it.bytesDone > 0 }
        alice.engine.cancel(active.id)
        assertEquals(SessionStatus.CANCELLED, awaitSession(alice) { it.status.isTerminal }.status)
        val bobSide = awaitSession(bob) { it.status.isTerminal }
        assertEquals(SessionStatus.CANCELLED, bobSide.status)
        delay(300)
        assertTrue(bob.receivedDir.listFiles().orEmpty().none { it.name.startsWith("huge") }, "partial file must be removed")
    }
}
