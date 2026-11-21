package volang

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import com.intellij.ui.dsl.builder.COLUMNS_LARGE
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel

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

    override fun apply() {
        super.apply()
        for (project in ProjectManager.getInstance().openProjects) {
            LspServerManager.getInstance(project).stopAndRestartIfNeeded(VoLspServerSupportProvider::class.java)
        }
    }
}

/**
 * Runs `vo lsp` (vo spec 038) for vo files: diagnostics and quick fixes, hover with documentation, go to
 * definition, find references, rename, formatting and the structure view all come from it. It reads open files
 * as the editor holds them and finds each file's program (the nearest `main.vo` above it) itself.
 */
class VoLspServerSupportProvider : LspServerSupportProvider {
    override fun fileOpened(project: Project, file: VirtualFile, serverStarter: LspServerSupportProvider.LspServerStarter) {
        if (file.fileType == VoFileType && VoSettings.binary() != null) serverStarter.ensureServerStarted(VoLspServerDescriptor(project))
    }
}

private class VoLspServerDescriptor(project: Project) : ProjectWideLspServerDescriptor(project, "vo") {
    override fun isSupportedFile(file: VirtualFile) = file.fileType == VoFileType

    override fun createCommandLine() =
        GeneralCommandLine(VoSettings.binary() ?: throw ExecutionException("vo not found: set it in Settings → Tools → vo"), "lsp")
}
