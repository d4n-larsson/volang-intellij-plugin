# vo for IntelliJ

IntelliJ support for [vo](https://github.com/d4n-larsson/volang), a small statically typed language. 

- Syntax highlighting for `.vo` source files, the `vo.effects` record and the `vo.packages` manifest
- Nord colors by default in dark themes (light themes use the IDE's colors)
- `Ctrl+/` toggles `#` line comments
- Bracket matching for `()`, `[]`, `{}`, and auto-closing brackets and quotes
- With the `vo` toolchain (Settings → Tools → vo, or `vo` on the PATH), the plugin runs `vo lsp`, vo's language server, so everything follows the checker, on unsaved text too:
  - errors and warnings as you type, with quick fixes (`Alt+Enter`)
  - hover and `Ctrl+Q`: the signature, documentation comment and example of what is under the cursor
  - `Ctrl+click` / `Ctrl+B` to the declaration, `Alt+F7` for every use, `Shift+F6` to rename
  - `Ctrl+Alt+L` formats as `vo fmt` does; the Structure view (`Ctrl+F12`) lists the file's items
- Without vo, `Ctrl+click` finds the `fn`/`record`/`variant` by name, also across `import`s and `vo.packages` packages (not `std`)

Works in IntelliJ-based IDEs 2026.2 and newer.

## Build and install

```sh
./gradlew buildPlugin
```

Then, in the IDE: **Settings → Plugins → ⚙️ → Install Plugin from Disk…** and pick `build/distributions/volang-intellij-plugin-<version>.zip`. Bump `version` in `gradle.properties` before rebuilding, or the IDE may not replace an installed copy with the same version.

## Develop

```sh
./gradlew test     # lexer, editor, navigation and language server tests
./gradlew runIde   # a sandbox IDE with the plugin installed
```

| File | Contents |
|---|---|
| `src/main/kotlin/volang/Vo.kt` | `.vo` file type, lexer, highlighter, commenter, brace matcher, quote handler |
| `src/main/kotlin/volang/VoNavigation.kt` | Go to declaration (`Ctrl+click`) without vo |
| `src/main/kotlin/volang/VoLsp.kt` | Starts `vo lsp`; the vo settings page |
| `src/main/kotlin/volang/VoEffects.kt` | `vo.effects` file type, lexer and highlighter |
| `src/main/kotlin/volang/VoPackages.kt` | `vo.packages` file type, lexer and highlighter |
| `src/main/resources/colorSchemes/VoNordDark.xml` | Nord colors for dark themes |
| `src/main/resources/META-INF/plugin.xml` | Plugin registration |

The keyword list in `Vo.kt` follows "Lexical rules" in `docs/reference.md` of the vo repository; update both together when the language gains a keyword.

Built from the [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template).
