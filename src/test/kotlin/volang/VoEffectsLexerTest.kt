package volang

import org.junit.Assert.assertEquals
import org.junit.Test

class VoEffectsLexerTest {
    private fun lex(src: String): List<String> {
        val l = VoEffectsLexer().apply { start(src, 0, src.length, 0) }
        return buildList {
            while (l.tokenType != null) {
                if (l.tokenType.toString() != "WHITE_SPACE") add("${l.tokenType}:${src.substring(l.tokenStart, l.tokenEnd)}")
                l.advance()
            }
        }
    }

    @Test fun function() = assertEquals(
        listOf("COMMENT:# vo effects record", "TYPE:main.vo:", "FUNCTION:main", "KEYWORD:uses", "EFFECT:console", "COMMA:,",
            "EFFECT:file", "DOT:.", "EFFECT:read", "LPAREN:(", "STRING:\"data/\"", "RPAREN:)"),
        lex("# vo effects record\nmain.vo:main uses console, file.read(\"data/\")"),
    )

    @Test fun testsAndPackages() = assertEquals(
        listOf("TYPE:main.vo:", "KEYWORD:test", "STRING:\"an empty report\"", "KEYWORD:pure",
            "TYPE:@util/text/pad.vo:", "FUNCTION:pure", "KEYWORD:pure",
            "TYPE:report.vo:", "FUNCTION:test", "KEYWORD:pure"),
        lex("main.vo:test \"an empty report\" pure\n@util/text/pad.vo:pure pure\nreport.vo:test pure"),
    )
}
