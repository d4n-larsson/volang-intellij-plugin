package volang

import org.junit.Assert.assertEquals
import org.junit.Test

class VoLexerTest {
    private fun lex(src: String): List<String> {
        val l = VoLexer().apply { start(src, 0, src.length, 0) }
        return buildList {
            while (l.tokenType != null) {
                if (l.tokenType.toString() != "WHITE_SPACE") add("${l.tokenType}:${src.substring(l.tokenStart, l.tokenEnd)}")
                l.advance()
            }
        }
    }

    @Test fun rangeIsNotFloat() = assertEquals(
        listOf("KEYWORD:for", "IDENT:i", "KEYWORD:in", "NUMBER:0", "DOT:.", "DOT:.", "NUMBER:3", "NUMBER:2.50"),
        lex("for i in 0..3 2.50"),
    )

    @Test fun declaration() = assertEquals(
        listOf("KEYWORD:fn", "FUNCTION:save", "LPAREN:(", "IDENT:p", "OPERATOR::", "TYPE:list", "LBRACKET:[", "TYPE:Person",
            "RBRACKET:]", "RPAREN:)", "OPERATOR:->", "TYPE:result", "LBRACKET:[", "TYPE:void", "RBRACKET:]", "KEYWORD:uses",
            "EFFECT:console", "LBRACE:{"),
        lex("fn save(p: list[Person]) -> result[void] uses console {"),
    )

    @Test fun export() = assertEquals(
        listOf("KEYWORD:export", "KEYWORD:fn", "FUNCTION:total", "LPAREN:(", "RPAREN:)"),
        lex("export fn total()"),
    )

    @Test fun newKeywords() = assertEquals(
        listOf("KEYWORD:export", "KEYWORD:fn", "KEYWORD:prop", "KEYWORD:requires", "KEYWORD:ensures"),
        lex("export fn prop requires ensures"),
    )

    @Test fun effectsOnlyInUsesClauses() = assertEquals(
        listOf("KEYWORD:uses", "EFFECT:serve", "LPAREN:(", "STRING:\"8000\"", "RPAREN:)", "COMMA:,", "EFFECT:file",
            "DOT:.", "EFFECT:read", "LPAREN:(", "STRING:\"site/\"", "RPAREN:)", "LBRACE:{", "FUNCTION:serve",
            "LPAREN:(", "IDENT:net", "RPAREN:)"),
        lex("uses serve(\"8000\"), file.read(\"site/\") { serve(net)"),
    )

    @Test fun clauseEndsAtFunctionTypeAndContracts() = assertEquals(
        listOf("IDENT:f", "OPERATOR::", "KEYWORD:fn", "LPAREN:(", "TYPE:T", "RPAREN:)", "KEYWORD:uses", "TYPE:E",
            "RPAREN:)", "KEYWORD:uses", "EFFECT:console", "COMMA:,", "TYPE:E", "KEYWORD:requires", "IDENT:env"),
        lex("f: fn(T) uses E) uses console, E requires env"),
    )

    @Test fun relexingFromAClauseState() {
        val l = VoLexer().apply { start("random {", 0, 8, 1) }
        assertEquals(VoTokens.EFFECT, l.tokenType)
        assertEquals(1, l.state)
    }

    @Test fun stringsAndComments() = assertEquals(
        listOf("COMMENT:# hi \"x\"", "STRING:\"a\\\"b\\\\\"", "SEMICOLON:;", "STRING:\"open", "IDENT:x", "BAD_CHARACTER:@"),
        lex("# hi \"x\"\n\"a\\\"b\\\\\";\n\"open\nx @"),
    )
}
