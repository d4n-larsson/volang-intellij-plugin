package volang

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.TokenType

/**
 * Ctrl+click without the vo toolchain: the `fn`/`record`/`variant` with the clicked name in this file or in the
 * file an `import ...name;` loads. With vo, `vo lsp` answers instead (VoLsp.kt). Token-based, so no scopes: a
 * local with the same name as a top-level item also jumps there.
 */
class VoGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(source: PsiElement?, offset: Int, editor: Editor?): Array<PsiElement>? {
        val type = source?.node?.elementType
        if (source == null || type !in NAMES || VoSettings.binary() != null) return null
        val file = source.containingFile as? VoFile ?: return null
        val name = source.text
        val target = declaration(file, name) ?: importedFile(file, name)?.let { declaration(it, name) }
        return target?.let { arrayOf(it) }
    }
}

private val NAMES = setOf(VoTokens.FUNCTION, VoTokens.IDENT, VoTokens.TYPE)
private val DECLARES = setOf("fn", "record", "variant")

private fun tokens(file: PsiFile) = generateSequence(file.firstChild) { it.nextSibling }
    .filter { it.node.elementType != TokenType.WHITE_SPACE && it.node.elementType != VoTokens.COMMENT }

/** The name token of `fn NAME`, `record NAME` or `variant NAME` in file. */
internal fun declaration(file: PsiFile, name: String): PsiElement? {
    var afterDeclare = false
    for (t in tokens(file)) {
        if (afterDeclare && t.text == name) return t
        afterDeclare = t.node.elementType == VoTokens.KEYWORD && t.text in DECLARES
    }
    return null
}

/**
 * The file that `import A.B.name;` in file loads, resolved like vo's loader: `std` is built into the binary
 * (null), a package listed in the nearest `vo.packages` is `DIRECTORY/B.vo`, else `A/B.vo` beside file.
 */
internal fun importedFile(file: PsiFile, name: String): PsiFile? {
    val dir = file.originalFile.virtualFile?.parent ?: return null
    val toks = tokens(file).iterator()
    while (toks.hasNext()) {
        val t = toks.next()
        if (t.node.elementType != VoTokens.KEYWORD || t.text != "import") continue
        val path = buildList {
            while (toks.hasNext()) {
                val p = toks.next()
                if (p.node.elementType == VoTokens.SEMICOLON) break
                if (p.node.elementType != VoTokens.DOT) add(p.text)
            }
        }
        if (path.size < 2 || path.last() != name) continue
        val segs = path.dropLast(1)
        if (segs[0] == "std") return null
        val vf = packageDir(dir, segs[0])?.findFileByRelativePath(segs.drop(1).joinToString("/") + ".vo")
            ?: dir.findFileByRelativePath(segs.joinToString("/") + ".vo")
            ?: return null
        return PsiManager.getInstance(file.project).findFile(vf)
    }
    return null
}

/** The directory of package name in the nearest `vo.packages` (`NAME DIRECTORY sha256:HASH` lines). */
private fun packageDir(from: VirtualFile, name: String): VirtualFile? {
    val list = generateSequence(from) { it.parent }.firstNotNullOfOrNull { it.findChild("vo.packages") } ?: return null
    return VfsUtilCore.loadText(list).lineSequence()
        .map { it.trim().split(Regex("\\s+")) }
        .firstOrNull { it.size >= 2 && it[0] == name }
        ?.let { list.parent.findFileByRelativePath(it[1]) }
}
