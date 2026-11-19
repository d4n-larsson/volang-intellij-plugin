package volang

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

/** Runs the real vo (../volang/bin/vo, or $VO) on files on disk; skipped when there is none. */
class VoQueryTest : BasePlatformTestCase() {
    private val vo = File(System.getenv("VO") ?: "../volang/bin/vo").absoluteFile
    private lateinit var dir: File

    private val main = "import geo.shapes.area;\n\nfn main() uses console {\n    let a = area(3, 4);\n    print(str(a));\n}\n"
    private val shapes = "export fn area(w: int, h: int) -> int {\n    return w * h;\n}\n"

    override fun setUp() {
        super.setUp()
        dir = FileUtil.createTempDirectory("vo-query", null)
        VfsRootAccess.allowRootAccess(testRootDisposable, dir.path)
        File(dir, "main.vo").writeText(main)
        File(dir, "geo").mkdir()
        File(dir, "geo/shapes.vo").writeText(shapes)
        VoSettings.path = vo.path
    }

    override fun tearDown() {
        try { VoSettings.path = ""; FileUtil.delete(dir) } finally { super.tearDown() }
    }

    override fun shouldRunTest() = super.shouldRunTest() && vo.canExecute().also { if (!it) println("skipped: no vo at $vo") }

    /** The token where text first occurs in file. */
    private fun at(file: String, text: String): PsiElement {
        val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(dir, file))!!
        val psi = PsiManager.getInstance(project).findFile(vf)!!
        return psi.findElementAt(psi.text.indexOf(text))!!
    }

    private fun targets(e: PsiElement) =
        VoGotoDeclarationHandler().getGotoDeclarationTargets(e, e.textRange.startOffset, null)
            ?.map { "${it.containingFile.name}:${it.textRange.startOffset}" }

    fun testCallGoesToImportedDeclaration() =
        assertEquals(listOf("shapes.vo:${shapes.indexOf("area")}"), targets(at("main.vo", "area(3")))

    fun testLocalGoesToItsLet() =
        assertEquals(listOf("main.vo:${main.indexOf("a = ")}"), targets(at("main.vo", "a)")))

    /** Ctrl+click on a declaration lists its uses; vo finds main.vo as the program's entry. */
    fun testDeclarationListsUses() =
        assertEquals(listOf("main.vo:${main.indexOf("area;")}", "main.vo:${main.indexOf("area(3")}"),
            targets(at("geo/shapes.vo", "area")))

    fun testBuiltinHasNoTarget() = assertNull(targets(at("main.vo", "print")))

    fun testTypeOnHover() {
        val e = at("main.vo", "a)")
        assertEquals("<code>a: int</code> <i>declared at main.vo:4</i>", VoDocumentationProvider().generateDoc(e, e))
    }
}
