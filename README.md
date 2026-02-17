<p align="center">
  <img src="docs/assets/icon.png" alt="iDevice Toolkit Logo" width="120" />
</p>

<h1 align="center">iDevice Toolkit</h1>

<p align="center">
  <strong>A powerful, open-source desktop application for browsing, extracting, and managing iPhone & iPad backups — with full encrypted backup support, media gallery, device control, and live screen mirroring.</strong>
</p>

<p align="center">
  <a href="#features">Features</a> •
  <a href="#screenshots">Screenshots</a> •
  <a href="#installation">Installation</a> •
  <a href="#building-from-source">Build</a> •
  <a href="#privacy">Privacy</a> •
  <a href="#credits">Credits</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/platform-Windows%20%7C%20macOS%20%7C%20Linux-blue" alt="Platforms" />
  <img src="https://img.shields.io/badge/java-18%2B-orange" alt="Java 18+" />
  <img src="https://img.shields.io/badge/javafx-23-green" alt="JavaFX 23" />
  <img src="https://img.shields.io/badge/license-open--source-brightgreen" alt="License" />
</p>

---

## Why iDevice Toolkit?

Most tools that handle **encrypted iOS backups** are either paid, limited trials, or lack a proper GUI. iDevice Toolkit is a free, open-source alternative that goes far beyond simple backup browsing:

- **Full encrypted backup support** — AES-256 decryption with Apple's KeyBag system, PBKDF2 key derivation, and protection class unwrapping
- **Media gallery** — Visual grid with thumbnails for all your photos and videos
- **Live device interaction** — Device info, screenshot capture, power controls, and real-time screen mirroring
- **File modification** — Replace or delete files directly inside backups (with automatic safety backups)
- **Zero network access** — Your data never leaves your machine

---

## Features

### Backup Explorer

| Feature | Description |
|---------|-------------|
| **Auto-discovery** | Automatically finds backups from default iTunes/Finder directories |
| **Encrypted backups** | Full AES-256 CBC decryption using Apple's KeyBag and protection class model |
| **Hierarchical browser** | Tree view organized by domain and path with context menu actions |
| **File search** | SQLite LIKE syntax with wildcards (`%`, `_`), sortable results, and bulk export |
| **File modification** | Replace files (re-encrypted automatically), delete entries, with incremental safety backups |
| **Drag & drop** | Load backup folders by dropping them into the app |
| **Multiple backups** | Open and switch between several backups simultaneously via sidebar |

### Media Gallery

| Feature | Description |
|---------|-------------|
| **Visual grid** | Thumbnail view of all photos and videos from Camera Roll and Media domains |
| **Filters** | Toggle between All / Photos / Videos |
| **Pagination** | Smooth browsing with 100 items per page and thumbnail caching |
| **Preview panel** | Full preview with file metadata and details |
| **Bulk export** | Export individual files or the entire gallery at once |
| **Format support** | JPG, PNG, HEIC, HEIF, GIF, BMP, TIFF, MOV, MP4, M4V, AVI |

### Device Integration

| Feature | Description |
|---------|-------------|
| **Device info** | Live device details — model, iOS version, serial, battery, disk usage |
| **Installed apps** | List of all user and system apps from connected devices |
| **Screenshots** | Capture device screen directly from the app |
| **Power controls** | Restart, shutdown, or sleep your device remotely |
| **Screen mirroring** | Real-time MJPEG streaming with interactive touch support (tap & swipe) |
| **iOS 17+ support** | Tunnel support via `pymobiledevice3` for modern iOS versions |

### Security & Privacy

- Decrypted database temp files are **securely zeroed out** and deleted on exit
- All backup modifications create **incremental safety backups** before any change
- **Path traversal protection** on file extraction
- **No internet connection** — the app is fully offline by design

---

## Screenshots

> Screenshots will be added here showcasing each major feature.

### Backup Browser

<!-- ![Backup Browser](docs/assets/screenshots/backup-browser.png) -->
*Hierarchical file tree with domain-based organization and context menu actions.*

### Media Gallery

<!-- ![Media Gallery](docs/assets/screenshots/media-gallery.png) -->
*Visual thumbnail grid with filters, pagination, and preview panel.*

### File Search

<!-- ![File Search](docs/assets/screenshots/file-search.png) -->
*Powerful search with SQLite LIKE syntax, sortable columns, and bulk export.*

### Device Info & Controls

<!-- ![Device Tab](docs/assets/screenshots/device-tab.png) -->
*Live device information with screenshot capture and power controls.*

### Screen Mirroring

<!-- ![Screen Mirroring](docs/assets/screenshots/screen-mirroring.png) -->
*Real-time screen mirroring with interactive touch support.*

### Dark & Light Themes

<!-- ![Themes](docs/assets/screenshots/themes.png) -->
*Full dark and light theme support.*

---

## Installation

### Windows

Download and run the `_win_x64.msi` installer from the [latest release](https://github.com/MaxiHuHe04/iTunes-Backup-Explorer/releases/latest).
It installs for the current user and doesn't require administrator privileges.

### macOS

| Processor | File |
|-----------|------|
| Apple Silicon (M1, M2, ...) | `_mac_arm64.dmg` |
| Intel | `_mac_x64.dmg` |

> **Note:** You may need to grant Full Disk Access under `System Settings > Privacy & Security > Full Disk Access` to prevent `Operation not permitted` errors when exporting files.

### Debian / Ubuntu

```bash
sudo apt install ./path/to/package_debian_x64.deb
```

The application installs to `/opt/itunes-backup-explorer` and appears in the desktop menu.

### Optional Dependencies

These are only needed for the **Device** and **Mirror** tabs:

| Dependency | Purpose |
|------------|---------|
| [libimobiledevice](https://libimobiledevice.org/) | Device info, app listing, screenshots, power controls |
| [pymobiledevice3](https://github.com/doronz88/pymobiledevice3) | Screen mirroring and iOS 17+ tunnel support (auto-installed via in-app wizard) |

---

## Building from Source

**Requirements:** JDK 18+ and Apache Maven.

### Native executable + installer

```bash
mvn clean package
```

The executable is generated in `target/app-image/` and the platform installer in `target/installer/`.

### Fat JAR (cross-platform)

```bash
# Current platform
mvn clean compile assembly:single

# Multi-platform (Windows + Linux + ARM macOS)
mvn clean compile assembly:single -Pmost_platforms
```

### Run directly (development)

```bash
mvn exec:exec
```

---

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Java 18 |
| GUI Framework | JavaFX 23 (FXML MVC) |
| Build System | Apache Maven |
| Cryptography | Bouncy Castle 1.80 (AES-256, PBKDF2, AES-Wrap) |
| Database | SQLite (Xerial sqlite-jdbc) |
| Plist Parsing | dd-plist |
| Screen Mirroring | pymobiledevice3 (Python) |
| Device Communication | libimobiledevice CLI |

---

## File Search Syntax

The search tab uses case-insensitive SQLite LIKE syntax with two wildcards:

- `%` — matches any sequence of zero or more characters
- `_` — matches any single character
- `\` — escape character

**Examples:**

| Goal | Domain | Relative Path |
|------|--------|---------------|
| Camera roll videos | `CameraRollDomain` | `%.mov` |
| Files under DCIM | `CameraRollDomain` | `Media/DCIM/%` |
| All SQLite databases | `%` | `%.sqlite` |
| WhatsApp files | `%whatsapp%` | `%` |
| iCloud app documents | `HomeDomain` | `Library/Mobile Documents/iCloud~%` |
| All files | `%` | `%` |

---

## Privacy

This application does **not** collect any data. It does not use an internet connection at any point. All processing happens entirely on your local machine.

---

## Credits

This project is a heavily extended fork of the original [iTunes Backup Explorer](https://github.com/MaxiHuHe04/iTunes-Backup-Explorer) by [MaxiHuHe04](https://github.com/MaxiHuHe04).

The original work was inspired by [this StackOverflow answer](https://stackoverflow.com/a/13793043/8868841) by [andrewdotn](https://stackoverflow.com/users/14558/andrewdotn), which provides a detailed explanation of how iOS backups are structured and encrypted.

**Key references:**
- [iPhone Data Protection in Depth](https://conference.hitb.org/hitbsecconf2011ams/materials/D2T2) (HITB SecConf 2011)
- [iphone-dataprotection](https://code.google.com/archive/p/iphone-dataprotection/) project
- [Forensic Analysis of iTunes Backups](http://www.farleyforensics.com/2019/04/14/forensic-analysis-of-itunes-backups/) by Jack Farley
- Apple iOS Security Guide

---

<p align="center">
  Made with ☕ and Java
</p>
