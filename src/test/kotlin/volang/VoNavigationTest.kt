package volang

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class VoNavigationTest : BasePlatformTestCase() {
    private fun target(): PsiTarget? {
        val at = myFixture.file.findElementAt(myFixture.caretOffset)
        val t = VoGotoDeclarationHandler().getGotoDeclarationTargets(at, myFixture.caretOffset, myFixture.editor)?.single()
        return t?.let { PsiTarget(it.containingFile.virtualFile.path.substringAfter("/src/"), it.textOffset) }
    }

    private data class PsiTarget(val file: String, val offset: Int)

    fun testSameFile() {
        myFixture.configureByText("main.vo", "fn main() {\n    hel<caret>per();\n}\n\nfn helper() {\n}\n")
        assertEquals(PsiTarget("main.vo", myFixture.file.text.indexOf("helper() {")), target())
    }

    fun testRecordInSameFile() {
        myFixture.configureByText("main.vo", "record Point {\n    x: int;\n}\n\nfn f(p: Poi<caret>nt) {\n}\n")
        assertEquals(PsiTarget("main.vo", "record ".length), target())
    }

    fun testRelativeImport() {
        myFixture.addFileToProject("geo/shapes.vo", "export record Point {\n    x: int;\n}\n\nexport fn dist2(p: Point) -> int {\n}\n")
        myFixture.configureByText("main.vo", "import geo.shapes.dist2;\n\nfn main() {\n    let d = di<caret>st2(p);\n}\n")
        assertEquals(PsiTarget("geo/shapes.vo", "export record Point {\n    x: int;\n}\n\nexport fn ".length), target())
    }

    fun testPackageImport() {
        myFixture.addFileToProject("vo.packages", "# packages\nmoney shared/money sha256:ABC\n")
        myFixture.addFileToProject("shared/money/cents.vo", "export fn add(a: int) -> int {\n}\n")
        myFixture.configureByText("main.vo", "import money.cents.add;\n\nfn main() {\n    a<caret>dd(1);\n}\n")
        assertEquals(PsiTarget("shared/money/cents.vo", "export fn ".length), target())
    }

    fun testStdAndUnknownHaveNoTarget() {
        myFixture.configureByText("main.vo", "import std.text.pad_left;\n\nfn main() {\n    pad_<caret>left(\"7\", 3, \"0\");\n    nope();\n}\n")
        assertNull(target())
    }
}
