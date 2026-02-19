# Changelog — iTunes Backup Explorer (Fork)

Documentacao de todas as melhorias implementadas sobre o [projeto original](https://github.com/MaxiHuHe04/iTunes-Backup-Explorer) de [MaxiHuHe04](https://github.com/MaxiHuHe04).

O projeto original oferecia navegacao basica de backups criptografados do iTunes. Este fork o transforma em um toolkit completo para iPhone, adicionando espelhamento de tela, galeria de midias, controle ao vivo do dispositivo, criacao de backups e uma interface moderna.

---

## Resumo das mudancas

| Area | O que era | O que virou |
|------|-----------|-------------|
| Interface | Tema claro basico, janela pequena | Tema dark profissional, 1200x700, barra de status, drag & drop, atalhos |
| Midias | Sem suporte visual | Galeria com thumbnails reais, filtros, paginacao, preview, suporte HEIC/MOV |
| Dispositivo | Sem comunicacao | Aba Device com info, bateria, armazenamento, apps, screenshot, controles |
| Espelhamento | Inexistente | AirPlay wireless + USB com toque interativo, iOS 17+ |
| Backup | So navegacao | Criacao de backup com progresso detalhado, ETA, velocidade |
| Busca | Basica, com bugs | Full-text em todas as colunas, filtros rapidos, export em lote |
| Apps | Listagem simples | Arvore de arquivos por app com export |
| Seguranca | Vazamentos de recursos | Path traversal protection, zeragem de temps, char[] para senhas, try-with-resources |
| Testes | Nenhum | 36 testes unitarios (JUnit 5) |
| Ferramentas | Requer instalacao manual | Download automatico de Python, ffmpeg, ImageMagick no Windows |

---

## Melhorias detalhadas

### 1. Redesign completo da interface

- Tema dark profissional com CSS redesenhado (+1000 linhas de estilos)
- Variantes light/dark selecionaveis nas preferencias
- Janela principal ampliada de ~800x600 para 1200x700
- Barra de status com total de arquivos, tamanho do backup e estado de criptografia
- Tela de boas-vindas fullscreen com gradiente quando nenhum backup esta aberto
- Drag & drop: arrastar pasta de backup direto na sidebar
- Atalhos de teclado: `Ctrl+O` (abrir), `Ctrl+F` (busca), `Ctrl+Q` (sair), `F5` (recarregar)
- Sidebar com ordenacao por data e context menu (abrir diretorio, fechar backup)
- Substituicao de todos os `System.out.println` e `printStackTrace` por SLF4J
- Formatacao de tamanhos de arquivo com utilitario `FileSize` (KB/MB/GB)

**Commits:** `e4885d4`, `6553149`, `5bf4409`, `4c82032`

### 2. Galeria de midias

Nova aba **Media** com navegacao visual de fotos e videos do backup:

- Grid de thumbnails com carregamento assincrono (pool de 4 threads)
- Cache LRU de thumbnails para navegacao fluida
- Filtros: All / Photos / Videos
- Paginacao com 100 itens por pagina
- Painel de preview full-size com metadados
- Suporte a HEIC/HEIF via ImageMagick (conversao para JPEG)
- Suporte a thumbnails de video (MOV/MP4/M4V/AVI) via ffmpeg (extrai frame)
- Export individual ou em lote
- Formatos: JPG, PNG, HEIC, HEIF, GIF, BMP, TIFF, MOV, MP4, M4V, AVI

**Commits:** `e2873d0`, `eff5f8d`

### 3. Controle ao vivo do dispositivo

Nova aba **Device** para comunicacao USB com iPhone via libimobiledevice:

- Deteccao automatica de iPhone conectado
- Informacoes do dispositivo: modelo, iOS, serial, UDID, Wi-Fi MAC
- Bateria: nivel e estado (carregando/descarregando)
- Armazenamento: barra visual com usado/total
- Apps instalados: listagem com filtro (User/System), busca e desinstalacao
- Captura de tela (screenshot direto para arquivo)
- Controles de energia: reiniciar, desligar, suspender
- Fallback via pymobiledevice3 quando libimobiledevice nao esta disponivel

**Commits:** `c9b26c9`, `a7597ba`, `08002d1`

### 4. Espelhamento de tela (Mirror)

Nova aba **Mirror** com dois modos de espelhamento:

**AirPlay (wireless):**
- Streaming via AirPlay usando uxplay como servidor
- Pipeline GStreamer com bifurcacao: jpegenc para frames + fakesink
- Parsing SOI/EOI para extracao de frames JPEG do pipe

**USB (direto):**
- Captura via pymobiledevice3 com DvtSecureSocketProxyService
- iOS 17+ com tunel automatico via tunneld (protocolo TCP)
- iOS 26+ fallback via DVT instruments

**Comum:**
- Toque interativo: tap e swipe encaminhados ao dispositivo via WDA HTTP
- FPS configuravel (30 por padrao)
- Modo view-only (sem envio de toques)
- Timeout de 30s no estado CONNECTING com monitoramento do processo
- Retry com 5 tentativas e cleanup de processos orfaos
- Estilos para dark/light theme na toolbar e badges de estado

**Commits:** `56ef57f`, `9b45aa6`, `4cf793d`

### 5. Criacao de backup

Novo botao **Create Backup** na tela inicial e no menu File:

- Detecta dispositivo via libimobiledevice ou pymobiledevice3
- Consulta informacoes do dispositivo para estimar tamanho total
- Janela de progresso detalhada:
  - Barra de progresso com percentual
  - Velocidade de transferencia em MB/s
  - Tempo restante estimado (ETA)
  - Bytes transferidos / total estimado
  - Log em tempo real
  - Marcos de progresso a cada 5%
- Suporte a dois backends:
  - `idevicebackup2` (libimobiledevice)
  - `pymobiledevice3 backup2` (fallback)
- Parser de progresso tqdm para pymobiledevice3 (leitura char a char para capturar `\r`)
- Cancelamento com confirmacao e destruicao de toda a arvore de processos
- Destino registrado automaticamente como backup root apos sucesso

**Commits:** `b2d1d44`, `eb26be2`, `2608fd7`, `3c581e1`, `85f09c9`, `73a4690`, `aac3909`, `57048da`, `42bb9a7`, `42b90ca`, `5082ebb`, `08002d1`, `917cf09`, `b18092a`, `991618b`, `45bcda9`

### 6. Setup automatico de ferramentas (Windows)

**Python portatil:**
- Quando Python nao esta no sistema, baixa automaticamente Python embeddable (~15 MB)
- Extrai em `~/.config/itunes-backup-explorer/python-portable/`
- Instala pip, setuptools, wheel e pymobiledevice3
- Setup disparado automaticamente ao clicar Create Backup
- Janela de progresso com log detalhado

**ffmpeg e ImageMagick portateis:**
- Quando ausentes no Windows, oferece download automatico (~130 MB total)
- ffmpeg: build GPL do BtbN/FFmpeg-Builds
- ImageMagick: build portable Q16-HDRI
- Armazenados em `~/.config/itunes-backup-explorer/ffmpeg-portable/` e `imagemagick-portable/`
- Dialogo de confirmacao ao abrir primeiro backup (uma vez por sessao)
- Ordem de deteccao: bundled no MSI > portatil no home > PATH do sistema
- No Linux: usa ferramentas do sistema normalmente, sem download

**Commits:** `473d040`, `8cec282`

### 7. Busca de arquivos melhorada

- Correcao de NPE que impedia a busca de funcionar
- Busca full-text em todas as colunas (fileID, domain, relativePath)
- Hint contextual explicando a sintaxe
- Colunas proporcionais sem scrollbar horizontal
- Filtros rapidos: Photos, Videos, WhatsApp, etc.
- Export em lote dos resultados

**Commits:** `884663d`

### 8. Apps browser

- Aba **Apps** com listagem de todos os apps do backup
- Arvore de diretorios expandivel por app
- Nome, bundle ID e versao de cada app
- Export de dados individuais
- Guard de backup locked para evitar erro ao acessar banco nao desbloqueado

**Commits:** `4526f22`

### 9. Preferencias expandidas

- Tema: Dark / Light
- Auto-select newest backup
- Limites de busca configuraveis
- Preservacao de timestamps na extracao
- Criacao de estrutura de diretorios na extracao
- Confirmacao de delete
- Gerenciamento de backup roots com deteccao de sobreposicao (pai/filho)
- Textos padronizados em ingles

**Commits:** `e2873d0`, `c520079`, `ff4d417`

### 10. Seguranca e robustez

- **Path traversal protection** na extracao de arquivos
- **Try-with-resources** em todos os PreparedStatement/ResultSet (corrige leak)
- **Byte overflow** corrigido na conversao de UID para indice no KeyBag
- **char[]** para senhas em vez de String, com limpeza apos uso
- **Zeragem de arquivo temporario** (banco decriptado) com `deleteOnExit`
- **Null safety** em campos opcionais do BackupManifest e BackupInfo
- **Acesso sincronizado** a conexao do banco de dados
- **Validacao de bounds** no acesso a objetos do BackupFile
- **Propagacao de excecoes** SQL em vez de engoli-las silenciosamente
- **DateTimeFormatter** em vez de SimpleDateFormat (thread-safe)
- **Locale.ROOT** em formatacao numerica para evitar virgula decimal
- Dependencia dd-plist migrada de JitPack para Maven Central

**Commits:** `8dec612`, `0f8fdd0`

### 11. Testes

36 testes unitarios adicionados (JUnit 5):

- `KeyBagUidTest` — conversao de UID para indice
- `BackupFilePaddingFixerTest` — deteccao/remocao de padding PKCS#7
- `BackupPathUtilsTest` — manipulacao de paths
- `FileSizeTest` — formatacao de tamanho legivel

**Commits:** `0f8fdd0`

### 12. Scripts de conveniencia

- `compile.bat` / `compile.sh` — compila o fat JAR com um duplo-clique
- `run.bat` / `run.sh` — compila (se necessario) e executa o app
- `scripts/run-dev.sh` — execucao em modo desenvolvimento via Maven
- Deteccao automatica do JAR sem versao hardcoded

**Commits:** `28ec3c1`

---

## Instrucoes para o usuario

### Pre-requisitos

| Requisito | Versao | Observacao |
|-----------|--------|------------|
| **Java (JDK)** | 18 ou superior | Necessario para compilar e rodar |
| **Apache Maven** | 3.8+ | Necessario para compilar |
| **Git** | qualquer | Para clonar o repositorio |

> No Windows, instale o JDK (ex: [Adoptium](https://adoptium.net/)) e adicione ao PATH.
> Maven pode ser instalado via [sdkman](https://sdkman.io/) ou [download direto](https://maven.apache.org/download.cgi).

### Instalacao rapida

```bash
git clone https://github.com/fredac100/iTunes-Backup-Explorer.git
cd iTunes-Backup-Explorer
```

**Windows:** duplo-clique em `run.bat`

**Linux/macOS:**
```bash
chmod +x run.sh
./run.sh
```

O script compila automaticamente na primeira execucao e abre o app.

### Dependencias opcionais

Essas ferramentas sao necessarias apenas para funcionalidades especificas. **No Windows, o app baixa automaticamente** quando necessario.

| Ferramenta | Funcionalidade | Linux (apt) | Windows |
|------------|---------------|-------------|---------|
| libimobiledevice | Device tab, backup via USB | `sudo apt install libimobiledevice-utils` | Incluido no Apple Devices / iTunes |
| pymobiledevice3 | Mirror tab, backup fallback | `pip install pymobiledevice3` | **Automatico** (baixa Python portatil se necessario) |
| ffmpeg | Thumbnails de video | `sudo apt install ffmpeg` | **Automatico** (baixa na primeira necessidade) |
| ImageMagick | Thumbnails de HEIC | `sudo apt install imagemagick libheif1` | **Automatico** (baixa na primeira necessidade) |
| uxplay | AirPlay wireless | `sudo apt install uxplay` | Nao disponivel |

### Como usar

#### Navegar backups

1. Abra o app — backups do iTunes/Finder sao detectados automaticamente
2. Se o backup esta criptografado, digite a senha quando solicitado
3. Navegue pelas abas: **Info**, **Files**, **Media**, **Apps**, **Search**
4. Para abrir um backup de outro local: `Ctrl+O` ou arraste a pasta na sidebar

#### Criar backup do iPhone

1. Conecte o iPhone via USB e toque "Confiar" no dispositivo
2. Clique em **Create Backup** na tela inicial
3. Escolha o destino
4. Acompanhe o progresso — cancelavel a qualquer momento

> No Windows, se nenhuma ferramenta de comunicacao estiver instalada, o app oferece instalar automaticamente.

#### Galeria de midias

1. Abra um backup e desbloqueie se necessario
2. Va para a aba **Media**
3. Use os filtros (All / Photos / Videos) e a paginacao
4. Clique em uma thumbnail para preview, duplo-clique para abrir o arquivo
5. Use **Export** para salvar arquivos individuais ou em lote

> No Windows, se thumbnails nao aparecem para videos ou HEIC, o app oferece baixar ffmpeg e ImageMagick automaticamente.

#### Espelhamento de tela

1. Va para a aba **Mirror**
2. Escolha **USB** (cabo) ou **AirPlay** (wireless)
3. Para USB: conecte o iPhone, o app detecta e inicia o stream
4. Para AirPlay: inicie a transmissao pelo Central de Controle do iPhone

#### Controle do dispositivo

1. Conecte o iPhone via USB
2. Va para a aba **Device**
3. Veja info, bateria, armazenamento e apps instalados
4. Use os botoes para screenshot, reiniciar, desligar ou suspender

### Compilacao avancada

```bash
# Fat JAR (todas as plataformas)
mvn clean compile assembly:single

# Installer nativo (MSI no Windows, DEB no Linux)
mvn clean package

# Fat JAR multi-plataforma (Windows + Linux + ARM macOS)
mvn clean compile assembly:single -Pmost_platforms
```

### Estrutura de diretorios do usuario

O app armazena dados em `~/.config/itunes-backup-explorer/`:

```
~/.config/itunes-backup-explorer/
    python-portable/          # Python embeddable (Windows, ~50 MB)
    python-venv/              # Virtualenv com pymobiledevice3 (Linux)
    ffmpeg-portable/          # ffmpeg portatil (Windows, ~85 MB)
    imagemagick-portable/     # ImageMagick portatil (Windows, ~50 MB)
```

Preferencias do usuario sao armazenadas via `java.util.prefs.Preferences` (registro do Windows ou `~/.java` no Linux).

---

## Arquitetura

```
me.maxih.itunes_backup_explorer/
    api/            Dominio e logica de backup (ITunesBackup, KeyBag, BackupFile)
    ui/             Controllers JavaFX (Window, Files, Media, Apps, Device, Mirror, Search, Preferences)
    util/           Utilitarios (DeviceService, MirrorService, MediaConverter, FileSize)
```

### Dependencias externas em runtime

| Componente | Tecnologia |
|------------|-----------|
| Linguagem | Java 18+ |
| GUI | JavaFX 23 (FXML + controllers) |
| Build | Apache Maven |
| Criptografia | Bouncy Castle 1.80 (AES-256, PBKDF2, AES-Wrap) |
| Banco de dados | SQLite (Xerial sqlite-jdbc 3.49) |
| Plist | dd-plist |
| Espelhamento | pymobiledevice3 (Python) |
| Dispositivo | libimobiledevice (CLI) |
| Video thumbnails | ffmpeg |
| HEIC thumbnails | ImageMagick |
| Testes | JUnit Jupiter 5 |

---

## Origem do projeto

Este projeto e um fork do [iTunes Backup Explorer](https://github.com/MaxiHuHe04/iTunes-Backup-Explorer) original de [MaxiHuHe04](https://github.com/MaxiHuHe04). O projeto original fornecia a base para navegacao de backups criptografados do iTunes com suporte a AES-256 e KeyBag da Apple. Este fork estende significativamente o projeto com espelhamento de tela, galeria de midias, controle ao vivo do dispositivo, criacao de backups, setup automatico de ferramentas e uma interface moderna redesenhada.
