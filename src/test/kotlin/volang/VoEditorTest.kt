package volang

import com.intellij.codeInsight.highlighting.BraceMatchingUtil
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class VoEditorTest : BasePlatformTestCase() {
    fun testCommentToggle() {
        myFixture.configureByText("a.vo", "<caret>let x = 1;")
        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_LINE)
        myFixture.checkResult("# let x = 1;")
        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_LINE)
        myFixture.checkResult("let x = 1;")
    }

    fun testQuotesAutoClose() {
        myFixture.configureByText("a.vo", "let s = <caret>;")
        myFixture.type("\"")
        myFixture.checkResult("let s = \"<caret>\";")
        myFixture.type("hi\"")
        myFixture.checkResult("let s = \"hi\"<caret>;")
    }

    fun testFileIcon() = assertEquals(16, VoFileType.icon.iconWidth)

    fun testEffectsFile() {
        myFixture.configureByText("vo.effects", "main.vo:main uses console")
        assertEquals(VoEffectsFileType, myFixture.file.virtualFile.fileType)
        val it = myFixture.editor.highlighter.createIterator("main.vo:".length)
        assertEquals(VoTokens.FUNCTION, it.tokenType)
    }

    fun testBraceMatch() {
        myFixture.configureByText("a.vo", "fn f(a: list[int]) {}")
        val it = myFixture.editor.highlighter.createIterator(myFixture.file.text.indexOf('['))
        assertTrue(BraceMatchingUtil.matchBrace(myFixture.file.text, myFixture.file.fileType, it, true))
        assertEquals(']', myFixture.file.text[it.start])
    }
}
