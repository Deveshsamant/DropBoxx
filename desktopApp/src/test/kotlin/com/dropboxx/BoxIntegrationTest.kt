package com.dropboxx

import com.dropboxx.domain.BoxAccess
import com.dropboxx.domain.BrowseOutcome
import com.dropboxx.model.AccessDecision
import com.dropboxx.model.ItemKind
import com.dropboxx.model.OutgoingItem
import com.dropboxx.model.Peer
import com.dropboxx.model.SessionStatus
import com.dropboxx.model.TransferSession
import com.dropboxx.platform.DesktopFile
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Pull model: Bob drops things in his box; Alice opens it and fetches. */
class BoxIntegrationTest {

    private lateinit var root: File
    private lateinit var alice: TestDevice
    private lateinit var bob: TestDevice
    private lateinit var bobPeer: Peer

    @BeforeTest
    fun setUp() = runBlocking<Unit> {
        root = Files.createTempDirectory("dropboxx-box").toFile()
        alice = TestDevice("Alice", root); bob = TestDevice("Bob", root)
        alice.start()
        bobPeer = assertNotNull(alice.discovery.addManual("127.0.0.1", bob.start()))
    }

    @AfterTest
    fun tearDown() { alice.stop(); bob.stop(); root.deleteRecursively() }

    private fun randomFile(name: String, size: Int) = File(root, name).also { it.writeBytes(Random.nextBytes(size)) }
    private fun sha256(f: File) = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
    private suspend fun awaitSession(d: TestDevice, p: (TransferSession) -> Boolean) = withTimeout(60_000) { d.engine.sessions.first { l -> l.any(p) }.first(p) }

    @Test
    fun boxSurvivesRestartAndPeerCanFetchAfterApproval() = runBlocking<Unit> {
        val video = randomFile("clip.bin", 4 * 1024 * 1024)
        val skipped = bob.box.add(listOf(OutgoingItem.File("a", DesktopFile(video)), OutgoingItem.Text("b", "note in the box"), OutgoingItem.Url("c", "https://example.org")))
        assertTrue(skipped.isEmpty())
        assertEquals(3, bob.box.items.value.size)
        // Persistence: a fresh repository over the same directory sees the same items.
        assertEquals(3, com.dropboxx.engine.box.BoxRepositoryImpl(bob.dir.absolutePath, bob.platform).items.value.size)

        // Alice asks; Bob is prompted and chooses "always allow".
        launch {
            val req = withTimeout(20_000) { bob.engine.accessRequest.filterNotNull().first() }
            assertEquals("Alice", req.requester.alias)
            bob.engine.respondAccess(req.id, AccessDecision(allow = true, always = true))
        }
        val outcome = alice.engine.browse(bobPeer)
        val ready = assertIs<BrowseOutcome.Ok>(outcome)
        assertEquals(setOf("clip.bin", "note in the box", "https://example.org"), ready.items.map { if (it.kind == ItemKind.FILE) it.name else it.content }.toSet())
        assertNotNull(alice.trust.outgoingToken(bob.identity.info.value.id), "always allow hands Alice a pair token")

        val sessionId = alice.engine.download(bobPeer, ready.items, ready.accessToken)
        val done = awaitSession(alice) { it.id == sessionId && it.status.isTerminal }
        assertEquals(SessionStatus.COMPLETED, done.status, done.error)
        assertEquals(sha256(video), sha256(File(alice.receivedDir, "clip.bin")))

        // Second visit needs no prompt: Alice is trusted now.
        assertIs<BrowseOutcome.Ok>(alice.engine.browse(bobPeer))
    }

    @Test
    fun deniedAndTrustedOnlyPolicies() = runBlocking<Unit> {
        bob.box.add(listOf(OutgoingItem.Text("t", "secret")))
        launch {
            val req = withTimeout(20_000) { bob.engine.accessRequest.filterNotNull().first() }
            bob.engine.respondAccess(req.id, AccessDecision(allow = false))
        }
        assertEquals(BrowseOutcome.Denied, alice.engine.browse(bobPeer))

        bob.settings.update { copy(boxAccess = BoxAccess.TRUSTED_ONLY) }
        assertEquals(BrowseOutcome.Denied, alice.engine.browse(bobPeer))

        bob.settings.update { copy(boxAccess = BoxAccess.EVERYONE) }
        val ok = assertIs<BrowseOutcome.Ok>(alice.engine.browse(bobPeer))
        assertEquals("secret", ok.items.single().content)
    }

    @Test
    fun downloadTokenIsRequired() = runBlocking<Unit> {
        bob.settings.update { copy(boxAccess = BoxAccess.EVERYONE) }
        bob.box.add(listOf(OutgoingItem.File("f", DesktopFile(randomFile("doc.bin", 10_000)))))
        val ready = assertIs<BrowseOutcome.Ok>(alice.engine.browse(bobPeer))
        val sessionId = alice.engine.download(bobPeer, ready.items, accessToken = "bogus")
        val s = awaitSession(alice) { it.id == sessionId && it.status.isTerminal }
        assertEquals(SessionStatus.COMPLETED_WITH_ERRORS, s.status)
        assertTrue(alice.receivedDir.listFiles().orEmpty().none { it.name.startsWith("doc") })
    }
}
