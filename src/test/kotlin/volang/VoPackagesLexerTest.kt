package volang

import org.junit.Assert.assertEquals
import org.junit.Test

class VoPackagesLexerTest {
    private fun lex(src: String, from: Int = 0, state: Int = 0): List<String> {
        val l = VoPackagesLexer().apply { start(src, from, src.length, state) }
        return buildList {
            while (l.tokenType != null) {
                if (l.tokenType.toString() != "WHITE_SPACE") add("${l.tokenType}:${src.substring(l.tokenStart, l.tokenEnd)}")
                l.advance()
            }
        }
    }

    @Test fun manifest() = assertEquals(
        listOf("COMMENT:# vo packages: update with `vo package ENTRY NAME DIRECTORY`",
            "TYPE:money", "STRING:shared/money", "NUMBER:sha256:ABC",
            "TYPE:lib", "STRING:../lib", "NUMBER:sha256:00", "BAD_CHARACTER:extra", "BAD_CHARACTER:#x"),
        lex("# vo packages: update with `vo package ENTRY NAME DIRECTORY`\nmoney shared/money sha256:ABC\n\n  lib ../lib sha256:00 extra #x\n"),
    )

    /** Relexing from the middle of a line starts with that token's recorded column. */
    @Test fun restartMidLine() = assertEquals(
        listOf("NUMBER:sha256:ABC", "TYPE:next"),
        lex("money shared/money sha256:ABC\nnext", from = "money shared/money ".length, state = 2),
    )
}
