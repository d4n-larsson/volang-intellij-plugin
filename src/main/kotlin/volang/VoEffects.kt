package volang

import com.intellij.lang.Language
import com.intellij.lexer.LexerBase
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

/** `vo.effects`, the effects record written by `vo effects --update`. Highlighting only: the file is generated. */
object VoEffectsLanguage : Language("vo effects")

object VoEffectsFileType : LanguageFileType(VoEffectsLanguage) {
    override fun getName() = "vo effects"
    override fun getDescription() = "vo effects record"
    override fun getDefaultExtension() = ""
    override fun getIcon() = VoFileType.icon
}

/**
 * One line per function or test: `main.vo:fetch uses net("localhost"), console`, `main.vo:test "name" pure`,
 * `@pkg/a.vo:f pure`. The `file.vo:` key is TYPE, the function FUNCTION, `pure`/`uses`/`test`/`prop` KEYWORD,
 * effect names EFFECT (as in a `uses` clause). Stateless like VoLexer, so relexing can restart at any token.
 */
class VoEffectsLexer : LexerBase() {
    private var buf: CharSequence = ""
    private var end = 0
    private var start = 0
    private var pos = 0
    private var type: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        buf = buffer; end = endOffset; pos = startOffset; advance()
    }

    override fun getState() = 0
    override fun getTokenType() = type
    override fun getTokenStart() = start
    override fun getTokenEnd() = pos
    override fun getBufferSequence() = buf
    override fun getBufferEnd() = end

    override fun advance() {
        start = pos
        if (pos >= end) { type = null; return }
        val c = buf[pos++]
        type = when {
            c.isWhitespace() -> { skip { it.isWhitespace() }; TokenType.WHITE_SPACE }
            c == '#' -> { skip { it != '\n' }; VoTokens.COMMENT }
            c == '"' -> { pos = stringEnd(buf, pos, end); VoTokens.STRING }
            c == '@' || c.isLetterOrDigit() || c == '_' -> {
                val colon = keyColon()
                if (colon >= 0) { pos = colon + 1; VoTokens.TYPE } else word()
            }
            c == '.' -> VoTokens.DOT
            c == ',' -> VoTokens.COMMA
            c == '(' -> VoTokens.LPAREN
            c == ')' -> VoTokens.RPAREN
            else -> TokenType.BAD_CHARACTER
        }
    }

    /** The `:` ending a `path.vo:` key that starts at `start`, or -1. */
    private fun keyColon(): Int {
        var i = start
        while (i < end && !buf[i].isWhitespace() && buf[i] != '"') {
            if (buf[i] == ':' && i - start >= 3 && buf.subSequence(i - 3, i).toString() == ".vo") return i
            i++
        }
        return -1
    }

    private fun word(): IElementType {
        skip { it.isLetterOrDigit() || it == '_' }
        val w = buf.subSequence(start, pos).toString()
        val afterKey = start > 0 && buf[start - 1] == ':'
        return when {
            afterKey && w in TESTS && at(pos) == ' ' && at(pos + 1) == '"' -> VoTokens.KEYWORD
            afterKey -> VoTokens.FUNCTION // a function may be named `pure`
            w == "pure" || w == "uses" -> VoTokens.KEYWORD
            w in VoTokens.EFFECTS -> VoTokens.EFFECT
            else -> VoTokens.IDENT
        }
    }

    private inline fun skip(p: (Char) -> Boolean) { while (pos < end && p(buf[pos])) pos++ }
    private fun at(i: Int) = if (i < end) buf[i] else null

    private companion object { val TESTS = setOf("test", "prop") }
}

class VoEffectsHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, file: VirtualFile?) = VoSyntaxHighlighter(::VoEffectsLexer)
}
