package volang

import com.intellij.platform.lsp.api.LspServerDescriptor
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class VoLspTest : BasePlatformTestCase() {
    override fun tearDown() {
        try { VoSettings.path = "" } finally { super.tearDown() }
    }

    /** The servers the provider asks for when fileName is opened. */
    private fun started(fileName: String): List<LspServerDescriptor> {
        val file = myFixture.configureByText(fileName, "").virtualFile
        val got = mutableListOf<LspServerDescriptor>()
        VoLspServerSupportProvider().fileOpened(project, file, object : LspServerSupportProvider.LspServerStarter {
            override fun ensureServerStarted(descriptor: LspServerDescriptor) { got += descriptor }
        })
        return got
    }

    fun testStartsVoLspForVoFiles() {
        VoSettings.path = "/opt/vo/bin/vo"
        val server = started("main.vo").single()
        assertEquals("/opt/vo/bin/vo lsp", server.createCommandLine().commandLineString)
        assertTrue(server.isSupportedFile(myFixture.file.virtualFile))
    }

    fun testNoServerForOtherFiles() {
        VoSettings.path = "/opt/vo/bin/vo"
        assertEmpty(started("notes.txt"))
    }
}
