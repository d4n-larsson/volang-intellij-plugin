package volang

import com.intellij.lang.Language
import com.intellij.lexer.LexerBase
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

/** `vo.packages`, the package manifest written by `vo package`. Highlighting only. */
object VoPackagesLanguage : Language("vo packages")

object VoPackagesFileType : LanguageFileType(VoPackagesLanguage) {
    override fun getName() = "vo packages"
    override fun getDescription() = "vo packages manifest"
    override fun getDefaultExtension() = ""
    override fun getIcon() = VoFileType.icon
}

/**
 * `# comment` lines and `NAME DIRECTORY sha256:HASH` lines (src/packages.c in vo). A word's column on its line
 * gives its token: NAME TYPE, DIRECTORY STRING, the hash NUMBER, any further word BAD_CHARACTER. The state is
 * that column, so relexing can restart at any token.
 */
class VoPackagesLexer : LexerBase() {
    private var buf: CharSequence = ""
    private var end = 0
    private var start = 0
    private var pos = 0
    private var type: IElementType? = null
    private var col = 0        // words before pos on its line, capped at 3
    private var tokenState = 0 // col at the start of the current token

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        buf = buffer; end = endOffset; pos = startOffset; col = initialState; advance()
    }

    override fun getState() = tokenState
    override fun getTokenType() = type
    override fun getTokenStart() = start
    override fun getTokenEnd() = pos
    override fun getBufferSequence() = buf
    override fun getBufferEnd() = end

    override fun advance() {
        start = pos
        tokenState = col
        if (pos >= end) { type = null; return }
        val c = buf[pos]
        type = when {
            c.isWhitespace() -> {
                while (pos < end && buf[pos].isWhitespace()) { if (buf[pos] == '\n') col = 0; pos++ }
                TokenType.WHITE_SPACE
            }
            col == 0 && c == '#' -> { while (pos < end && buf[pos] != '\n') pos++; VoTokens.COMMENT }
            else -> {
                while (pos < end && !buf[pos].isWhitespace()) pos++
                COLUMNS.getOrElse(col) { TokenType.BAD_CHARACTER }.also { col = minOf(col + 1, 3) }
            }
        }
    }

    private companion object { val COLUMNS = listOf(VoTokens.TYPE, VoTokens.STRING, VoTokens.NUMBER) }
}

class VoPackagesHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, file: VirtualFile?) = VoSyntaxHighlighter(::VoPackagesLexer)
}
