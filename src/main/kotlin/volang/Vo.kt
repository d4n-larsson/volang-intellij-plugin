package volang

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ASTNode
import com.intellij.lang.BracePair
import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import com.intellij.lang.Commenter
import com.intellij.lang.Language
import com.intellij.lang.PairedBraceMatcher
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.lexer.LexerBase
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as C
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

object VoLanguage : Language("vo")

object VoFileType : LanguageFileType(VoLanguage) {
    override fun getName() = "vo"
    override fun getDescription() = "vo source file"
    override fun getDefaultExtension() = "vo"
    override fun getIcon() = ICON
    private val ICON = IconLoader.getIcon("/icons/vo.svg", VoFileType::class.java)
}

class VoToken(name: String) : IElementType(name, VoLanguage)

object VoTokens {
    val KEYWORD = VoToken("KEYWORD")
    val TYPE = VoToken("TYPE")          // built-in types and PascalCase record/variant names
    val IDENT = VoToken("IDENT")
    val FUNCTION = VoToken("FUNCTION")  // identifier directly followed by `(`
    val NUMBER = VoToken("NUMBER")
    val STRING = VoToken("STRING")
    val COMMENT = VoToken("COMMENT")
    val OPERATOR = VoToken("OPERATOR")
    val DOT = VoToken("DOT")
    val COMMA = VoToken("COMMA")
    val SEMICOLON = VoToken("SEMICOLON")
    val LPAREN = VoToken("LPAREN")
    val RPAREN = VoToken("RPAREN")
    val LBRACKET = VoToken("LBRACKET")
    val RBRACKET = VoToken("RBRACKET")
    val LBRACE = VoToken("LBRACE")
    val RBRACE = VoToken("RBRACE")
    val EFFECT = VoToken("EFFECT")      // an effect name in a `uses` clause (and in vo.effects)
    val ANNOTATION = VoToken("ANNOTATION") // `@spec` (specs/012-spec-annotations); its arguments lex as usual

    // docs/reference.md "Lexical rules"
    val KEYWORDS = setOf(
        "fn", "record", "variant", "test", "import", "let", "var", "set", "if", "else", "while", "for", "in",
        "match", "case", "return", "break", "continue", "try", "new", "and", "or", "not", "true", "false", "uses",
        "prop", "requires", "ensures", "export",
    )
    // the effects of docs/reference.md "Effects" (file.read and file.write are two words and a dot)
    val EFFECTS = setOf("console", "input", "file", "read", "write", "net", "clock", "random", "env", "serve")
    val TYPES = setOf("int", "float", "bool", "str", "list", "map", "result", "void")
}

/**
 * vo has no multi-line constructs; the one state is where a token stands in a `uses` clause, so effect
 * names there are EFFECT: 0 outside, 1 in the clause, 2 inside a scope's parentheses. The clause ends at
 * anything that is not an effect name, a type parameter, `,`, `.` or a scope (`{`, `;`, a `)` closing a
 * function type, `requires`). Relexing restarts at any token with its recorded state.
 */
class VoLexer : LexerBase() {
    private var buf: CharSequence = ""
    private var end = 0
    private var start = 0
    private var pos = 0
    private var type: IElementType? = null
    private var state = 0      // after the current token
    private var tokenState = 0 // at the start of the current token

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        buf = buffer; end = endOffset; pos = startOffset; state = initialState; advance()
    }

    override fun getState() = tokenState
    override fun getTokenType() = type
    override fun getTokenStart() = start
    override fun getTokenEnd() = pos
    override fun getBufferSequence() = buf
    override fun getBufferEnd() = end

    override fun advance() {
        tokenState = state
        lex()
        val t = type ?: return
        when (state) {
            1 -> when {
                t == VoTokens.LPAREN -> state = 2
                t == TokenType.WHITE_SPACE || t == VoTokens.COMMENT || t == VoTokens.COMMA || t == VoTokens.DOT -> {}
                t == VoTokens.TYPE && pos - start == 1 -> {} // an effect parameter (E)
                (t == VoTokens.IDENT || t == VoTokens.FUNCTION || t == VoTokens.KEYWORD) &&
                    buf.subSequence(start, pos).toString() in VoTokens.EFFECTS -> type = VoTokens.EFFECT
                else -> state = 0
            }
            2 -> if (t == VoTokens.RPAREN) state = 1
        }
        if (t == VoTokens.KEYWORD && pos - start == 4 && buf.subSequence(start, pos).toString() == "uses") state = 1
    }

    private fun lex() {
        start = pos
        if (pos >= end) { type = null; return }
        val c = buf[pos++]
        type = when {
            c.isWhitespace() -> { skip { it.isWhitespace() }; TokenType.WHITE_SPACE }
            c == '#' -> { skip { it != '\n' }; VoTokens.COMMENT }
            c == '"' -> { string(); VoTokens.STRING }
            c.isDigit() -> {
                skip { it.isDigit() }
                // `1.5` is a float, but `0..3` is a range
                if (at(pos) == '.' && at(pos + 1)?.isDigit() == true) { pos++; skip { it.isDigit() } }
                VoTokens.NUMBER
            }
            c.isLetter() || c == '_' -> {
                skip { it.isLetterOrDigit() || it == '_' }
                val word = buf.subSequence(start, pos).toString()
                when {
                    word in VoTokens.KEYWORDS -> VoTokens.KEYWORD
                    word in VoTokens.TYPES || c.isUpperCase() -> VoTokens.TYPE
                    at(pos) == '(' -> VoTokens.FUNCTION
                    else -> VoTokens.IDENT
                }
            }
            c == '@' && at(pos)?.isLetter() == true -> { skip { it.isLetterOrDigit() || it == '_' }; VoTokens.ANNOTATION }
            c in OPS -> { skip { it in OPS }; VoTokens.OPERATOR }
            c == '.' -> VoTokens.DOT
            c == ',' -> VoTokens.COMMA
            c == ';' -> VoTokens.SEMICOLON
            c == '(' -> VoTokens.LPAREN
            c == ')' -> VoTokens.RPAREN
            c == '[' -> VoTokens.LBRACKET
            c == ']' -> VoTokens.RBRACKET
            c == '{' -> VoTokens.LBRACE
            c == '}' -> VoTokens.RBRACE
            else -> TokenType.BAD_CHARACTER
        }
    }

    private fun string() { pos = stringEnd(buf, pos, end) }

    private inline fun skip(p: (Char) -> Boolean) { while (pos < end && p(buf[pos])) pos++ }
    private fun at(i: Int) = if (i < end) buf[i] else null

    private companion object { const val OPS = "+-*/%<>=!:?&|" }
}

/** The end of a string whose opening quote ends at pos. Strings are single-line: an unclosed one ends at the newline. */
internal fun stringEnd(buf: CharSequence, from: Int, end: Int): Int {
    var pos = from
    while (pos < end) {
        when (buf[pos]) {
            '"' -> return pos + 1
            '\n' -> return pos
            '\\' -> pos += if (pos + 1 < end && buf[pos + 1] == '\n') 1 else 2
            else -> pos++
        }
    }
    return minOf(pos, end)
}

/** Also highlights `vo.effects` (VoEffectsLexer), which makes the same token types. */
class VoSyntaxHighlighter(private val lexer: () -> Lexer = ::VoLexer) : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = lexer()
    override fun getTokenHighlights(t: IElementType): Array<TextAttributesKey> = pack(KEYS[t])

    private companion object {
        fun key(name: String, fallback: TextAttributesKey) = TextAttributesKey.createTextAttributesKey("VO_$name", fallback)
        val PAREN = key("PAREN", C.PARENTHESES)
        val BRACKET = key("BRACKET", C.BRACKETS)
        val BRACE = key("BRACE", C.BRACES)
        val KEYS = mapOf(
            VoTokens.KEYWORD to key("KEYWORD", C.KEYWORD),
            VoTokens.EFFECT to key("EFFECT", C.METADATA),
            VoTokens.ANNOTATION to key("ANNOTATION", C.METADATA),
            VoTokens.TYPE to key("TYPE", C.CLASS_NAME),
            VoTokens.FUNCTION to key("FUNCTION", C.FUNCTION_CALL),
            VoTokens.IDENT to key("IDENT", C.IDENTIFIER),
            VoTokens.NUMBER to key("NUMBER", C.NUMBER),
            VoTokens.STRING to key("STRING", C.STRING),
            VoTokens.COMMENT to key("COMMENT", C.LINE_COMMENT),
            VoTokens.OPERATOR to key("OPERATOR", C.OPERATION_SIGN),
            VoTokens.DOT to key("DOT", C.DOT),
            VoTokens.COMMA to key("COMMA", C.COMMA),
            VoTokens.SEMICOLON to key("SEMICOLON", C.SEMICOLON),
            VoTokens.LPAREN to PAREN,
            VoTokens.RPAREN to PAREN,
            VoTokens.LBRACKET to BRACKET,
            VoTokens.RBRACKET to BRACKET,
            VoTokens.LBRACE to BRACE,
            VoTokens.RBRACE to BRACE,
            TokenType.BAD_CHARACTER to key("BAD_CHARACTER", HighlighterColors.BAD_CHARACTER),
        )
    }
}

class VoSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, file: VirtualFile?) = VoSyntaxHighlighter()
}

class VoFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, VoLanguage) {
    override fun getFileType() = VoFileType
}

/** Flat PSI (file -> tokens). It exists only so editor actions like comment toggling get a PsiFile. */
class VoParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?): Lexer = VoLexer()
    override fun createParser(project: Project?) = PsiParser { root, builder ->
        val m = builder.mark()
        while (!builder.eof()) builder.advanceLexer()
        m.done(root)
        builder.treeBuilt
    }
    override fun getFileNodeType() = FILE
    override fun getCommentTokens() = TokenSet.create(VoTokens.COMMENT)
    override fun getStringLiteralElements() = TokenSet.create(VoTokens.STRING)
    override fun createElement(node: ASTNode): PsiElement = throw UnsupportedOperationException(node.elementType.toString())
    override fun createFile(viewProvider: FileViewProvider) = VoFile(viewProvider)

    private companion object { val FILE = IFileElementType(VoLanguage) }
}

class VoCommenter : Commenter {
    override fun getLineCommentPrefix() = "# "
    override fun getBlockCommentPrefix() = null
    override fun getBlockCommentSuffix() = null
    override fun getCommentedBlockCommentPrefix() = null
    override fun getCommentedBlockCommentSuffix() = null
}

class VoBraceMatcher : PairedBraceMatcher {
    override fun getPairs() = PAIRS
    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?) = true
    override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int) = openingBraceOffset

    private companion object {
        val PAIRS = arrayOf(
            BracePair(VoTokens.LPAREN, VoTokens.RPAREN, false),
            BracePair(VoTokens.LBRACKET, VoTokens.RBRACKET, false),
            BracePair(VoTokens.LBRACE, VoTokens.RBRACE, true),
        )
    }
}

class VoQuoteHandler : SimpleTokenSetQuoteHandler(VoTokens.STRING)
