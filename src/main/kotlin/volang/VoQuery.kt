package volang

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.ui.dsl.builder.COLUMNS_LARGE
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.concurrency.AppExecutorUtil
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Where the `vo` binary is: Settings → Tools → vo, else `vo` on the PATH. */
object VoSettings {
    private const val KEY = "volang.vo.path"

    var path: String
        get() = PropertiesComponent.getInstance().getValue(KEY, "")
        set(value) = PropertiesComponent.getInstance().setValue(KEY, value.trim(), "")

    fun binary(): String? = path.ifBlank { null }?.let(FileUtil::expandUserHome)
        ?: PathEnvironmentVariableUtil.findInPath("vo")?.path
}

class VoConfigurable : BoundConfigurable("vo") {
    override fun createPanel() = panel {
        row("vo binary:") {
            textField().bindText(VoSettings::path).columns(COLUMNS_LARGE)
                .comment("For example ~/volang/bin/vo. Leave empty to use <code>vo</code> on the PATH.")
        }
    }
}

/** A position vo answered with. */
data class VoPos(val file: VirtualFile, val offset: Int, val line: Int) {
    fun element(project: Project): PsiElement? = PsiManager.getInstance(project).findFile(file)?.findElementAt(offset)
}

/** What `vo query type-at` says about a position: the name there, its type, its declaration (none for builtins). */
data class VoTypeAt(val name: String, val type: String, val declaration: VoPos?)

/**
 * Answers from `vo query` (spec 006), which follow the checker. Every call returns null when vo cannot answer:
 * no binary, unsaved vo files (vo reads the disk; they are saved for the next call), a program with errors,
 * a file outside the local file system, or a run over 3 seconds. Callers then fall back to the token-based code.
 */
object VoQuery {
    fun typeAt(file: VirtualFile, offset: Int): VoTypeAt? {
        val o = ask(file, offset, "type-at", "type_at")?.firstOrNull() ?: return null
        return VoTypeAt(o.str("name") ?: o.str("text") ?: return null, o.str("type") ?: return null,
            o.getAsJsonObject("declaration")?.let(::pos))
    }

    /** Every use of what is at offset (a declaration's position also works), without the declaration itself. */
    fun refs(file: VirtualFile, offset: Int): List<VoPos>? =
        ask(file, offset, "refs", "ref")?.filter { it.str("role") != "declaration" }?.mapNotNull(::pos)

    /** The answer records of kind `kind`, asked first of the program file belongs to, then of file alone. */
    private fun ask(file: VirtualFile, offset: Int, question: String, kind: String): List<JsonObject>? {
        if (!file.isInLocalFileSystem || !allSaved()) return null
        val doc = FileDocumentManager.getInstance().getDocument(file) ?: return null
        val line = doc.getLineNumber(offset)
        val col = Character.codePointCount(doc.immutableCharSequence, doc.getLineStartOffset(line), offset) + 1
        for (entry in listOf(entry(file), file).distinct()) {
            val answer = run(listOf("query", question, "--json", entry.path, "${file.path}:${line + 1}:$col"), entry.parent)
                ?: return null
            answer.filter { it.str("kind") == kind }.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }

    /** vo reads files from disk: with unsaved vo files the answer would be about old text, so save them and wait. */
    private fun allSaved(): Boolean {
        val fdm = FileDocumentManager.getInstance()
        val unsaved = fdm.unsavedDocuments.filter { fdm.getFile(it)?.fileType == VoFileType }
        if (unsaved.isEmpty()) return true
        ApplicationManager.getApplication().invokeLater({ unsaved.forEach(fdm::saveDocument) }, ModalityState.nonModal())
        return false
    }

    /**
     * The program's entry: a `.vo` with `fn main` in file's directory or the nearest one above it, not past the
     * directory with `vo.effects` (imports only go down, so the entry is never below the file). Else file itself.
     */
    // ponytail: reads the .vo files of those directories on every query; cache per directory if it shows up
    private fun entry(file: VirtualFile): VirtualFile {
        var dir = file.parent
        while (dir != null) {
            dir.children.firstOrNull { it.extension == "vo" && "fn main(" in VfsUtilCore.loadText(it) }?.let { return it }
            if (dir.findChild("vo.effects") != null) break
            dir = dir.parent
        }
        return file
    }

    private fun run(args: List<String>, dir: VirtualFile): List<JsonObject>? {
        val bin = VoSettings.binary() ?: return null
        val process = try {
            ProcessBuilder(listOf(bin) + args).directory(File(dir.path))
                .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        } catch (_: IOException) {
            return null
        }
        val kill = AppExecutorUtil.getAppScheduledExecutorService().schedule({ process.destroyForcibly() }, 3, TimeUnit.SECONDS)
        val out = process.inputStream.bufferedReader().use { it.readText() }
        kill.cancel(false)
        return out.lines().filter { it.startsWith("{") }
            .mapNotNull { runCatching { JsonParser.parseString(it).asJsonObject }.getOrNull() }
    }

    /** `{"file": ..., "start": {"line": L, "col": C}}`, C counted in characters, to a document offset. */
    private fun pos(o: JsonObject): VoPos? = runCatching {
        val file = LocalFileSystem.getInstance().findFileByPath(o.str("file")!!)!!
        val doc = FileDocumentManager.getInstance().getDocument(file)!!
        val start = o.getAsJsonObject("start")
        val line = start["line"].asInt
        VoPos(file, Character.offsetByCodePoints(doc.immutableCharSequence, doc.getLineStartOffset(line - 1), start["col"].asInt - 1), line)
    }.getOrNull()

    private fun JsonObject.str(key: String) = get(key)?.takeIf { it.isJsonPrimitive }?.asString
}

/** The type of the name under the mouse or caret, from `vo query type-at`: hover, Ctrl+hover and Ctrl+Q. */
class VoDocumentationProvider : AbstractDocumentationProvider() {
    override fun getCustomDocumentationElement(editor: Editor, file: PsiFile, contextElement: PsiElement?, targetOffset: Int) =
        contextElement?.takeIf { file is VoFile && it.node.elementType in NAMES }

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?) = describe(originalElement ?: element)
    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?) = describe(originalElement ?: element)

    private fun describe(e: PsiElement?): String? {
        if (e?.node?.elementType !in NAMES) return null
        val vf = e!!.containingFile?.virtualFile ?: return null
        val at = VoQuery.typeAt(vf, e.textRange.startOffset) ?: return null
        val where = at.declaration?.let { " <i>declared at ${StringUtil.escapeXmlEntities(it.file.name)}:${it.line}</i>" }.orEmpty()
        return "<code>${StringUtil.escapeXmlEntities("${at.name}: ${at.type}")}</code>$where"
    }
}

/** The tokens vo can answer about: names of functions, variables, fields, records and variants. */
internal val NAMES = setOf(VoTokens.FUNCTION, VoTokens.IDENT, VoTokens.TYPE)
