# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Projeto

iTunes Backup Explorer — aplicação desktop JavaFX para explorar backups criptografados do iPhone, espelhar tela via AirPlay, gerenciar apps e navegar mídias. Fork estendido de MaxiHuHe04/iTunes-Backup-Explorer.

## Comandos

| Ação | Comando |
|------|---------|
| Rodar em dev | `mvn -q -DskipTests compile exec:exec@run-java` |
| Build completo (installer nativo) | `mvn clean package` |
| Build fat JAR | `mvn clean compile assembly:single` |
| Fat JAR multi-plataforma | `mvn clean compile assembly:single -Pmost_platforms` |
| Testes | `mvn test` |
| Teste único | `mvn test -Dtest=NomeDaClasse#nomeDoMetodo` |

Script de dev: `./scripts/run-dev.sh`

## Stack

- **Java 18+** com JPMS (module-info.java)
- **JavaFX 23** (FXML + controllers)
- **Maven** como build system
- **SQLite** (Xerial jdbc 3.49) para Manifest.db dos backups
- **Bouncy Castle** (bcprov-jdk18on 1.80) para criptografia AES-256
- **dd-plist** para parsing de plists Apple
- **JUnit Jupiter 5** para testes
- **pymobiledevice3** (Python) para AirPlay/mirroring
- **libimobiledevice** (CLI) para comunicação com dispositivos

## Arquitetura

Quatro pacotes sob `me.maxih.itunes_backup_explorer`:

### `api/` — Domínio e lógica de backup
- `ITunesBackup` — modelo central: carrega diretório do backup, gerencia conexão SQLite, consulta arquivos, re-encripta
- `BackupManifest` / `BackupInfo` — parsing de `Manifest.plist` e `Info.plist`
- `BackupFile` — representa entrada do manifest SQLite; extract, replace, delete de arquivos individuais
- `KeyBag` — criptografia completa: PBKDF2 unlock (SHA-256 + SHA-1), AES-Wrap para class keys, AES-256-CBC para decrypt/encrypt de arquivos

### `ui/` — Controllers JavaFX
Cada tab tem seu controller FXML:
- `WindowController` — controller raiz: sidebar de backups, troca de tabs, drag & drop, status bar, criação de backup
- `FilesTabController` — tree browser de arquivos por domínio
- `FileSearchTabController` — busca com SQL LIKE + ordenação + export em lote
- `MediaTabController` — galeria de fotos/vídeos com thumbnails, paginação, export em lote
- `AppsTabController` — tree de arquivos por app
- `DeviceTabController` — info ao vivo do dispositivo via libimobiledevice
- `MirrorTabController` — espelhamento AirPlay/USB com encaminhamento de toques
- `PreferencesController` — preferências do app (backup roots, tema, opções de export)
- `ThumbnailService` — thread pool para carregamento assíncrono de thumbnails com cache LRU

### `util/` — Utilitários
- `DeviceService` — executa CLIs do libimobiledevice (`idevice_id`, `ideviceinfo`, `ideviceinstaller`, etc.)
- `MirrorService` — gerencia subprocess `mirror_stream.py`, AirPlay via pymobiledevice3, encaminhamento de toques via WDA HTTP
- `MediaConverter` — converte HEIC/HEIF para JPEG

### Entry points
- `ITunesBackupExplorer` — `Application.launch()`, cria Scene, aplica tema
- `ITunesBackupExplorerLauncher` — entry point não-modular para o fat JAR

## Fluxo principal

1. `WindowController.initialize()` → carrega backup roots das preferences → escaneia diretórios por `Manifest.db` + `Manifest.plist`
2. Se encriptado: `KeyBag.unlock(password)` → PBKDF2 → AES-Wrap unwrap das class keys
3. `decryptDatabase()` → decripta `Manifest.db` para temp file via AES-256-CBC
4. Queries SQL na tabela `files` do SQLite decriptado
5. `BackupFile.extract()` → decripta arquivos individuais da estrutura `xx/xxxx...` usando chave do manifest

## Preferências do usuário

Armazenadas via `java.util.prefs.Preferences` (não em arquivo), sob chave `me.maxih.itunes_backup_explorer`. Chaves principais: `BackupRoots`, `Theme`, `PreserveTimestamps`, `CreateDirectoryStructure`, `AutoSelectNewestBackup`, `SearchResultLimit`.

## Testes

Em `src/test/java/me/maxih/itunes_backup_explorer/`:
- `api/KeyBagUidTest` — conversão de UID para índice
- `util/BackupFilePaddingFixerTest` — detecção/remoção de padding PKCS#7
- `util/BackupPathUtilsTest` — manipulação de paths
- `util/FileSizeTest` — formatação de tamanho legível
