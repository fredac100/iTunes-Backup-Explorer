<h1 align="center">iDevice Toolkit</h1>

<p align="center">
  <strong>The open-source iTunes alternative for Linux.<br>Mirror your iPhone screen wirelessly, browse encrypted backups, manage apps, and explore your media — all from your desktop.</strong>
</p>

<p align="center">
  <a href="#airplay-screen-mirroring">AirPlay Mirror</a> •
  <a href="#media-gallery">Media Gallery</a> •
  <a href="#device-control">Device Control</a> •
  <a href="#backup-explorer">Backup Explorer</a> •
  <a href="#screenshots">Screenshots</a> •
  <a href="#installation">Installation</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/platform-Linux%20%7C%20Windows%20%7C%20macOS-blue" alt="Platforms" />
  <img src="https://img.shields.io/badge/java-18%2B-orange" alt="Java 18+" />
  <img src="https://img.shields.io/badge/javafx-23-green" alt="JavaFX 23" />
  <img src="https://img.shields.io/badge/license-MIT-brightgreen" alt="MIT License" />
  <img src="https://img.shields.io/github/v/release/fredac100/iTunes-Backup-Explorer" alt="Release" />
</p>

---

## What is iDevice Toolkit?

Apple never made iTunes for Linux. iDevice Toolkit fills that gap — and goes beyond.

It's a **free, open-source desktop app** that connects your iPhone to your Linux (or Windows/macOS) machine for real-time interaction and full backup management. No cloud, no subscriptions, no data collection.

| What you get | How it works |
|---|---|
| **Wireless screen mirroring** | AirPlay streaming with low latency — no cable needed |
| **Full media gallery** | Browse your photos and videos with real thumbnails, not just file names |
| **Live device control** | Battery, storage, apps, screenshots, power controls — all via USB |
| **Encrypted backup support** | AES-256 decryption with Apple's KeyBag system |
| **Offline by design** | Zero network access — your data stays on your machine |

---

## Key Features

### AirPlay Screen Mirroring

| Feature | Description |
|---------|-------------|
| **Wireless mirroring** | Stream your iPhone screen via AirPlay — no cable required |
| **USB mirroring** | Alternative low-latency option via direct USB connection |
| **Interactive touch** | Tap and swipe gestures forwarded to the device in real time |
| **Configurable FPS** | 30 FPS default, adjustable for performance |
| **View-only mode** | Watch without sending touch events |
| **iOS 17+ support** | Tunnel support via `pymobiledevice3` for modern iOS versions |

### Media Gallery

| Feature | Description |
|---------|-------------|
| **Visual grid** | Real thumbnail previews of all photos and videos — not just metadata |
| **Filters** | Toggle between All / Photos / Videos |
| **Pagination** | Smooth browsing with 100 items per page and thumbnail caching |
| **Preview panel** | Full-size preview with file metadata and details |
| **Bulk export** | Export individual files or the entire gallery at once |
| **Format support** | JPG, PNG, HEIC, HEIF, GIF, BMP, TIFF, MOV, MP4, M4V, AVI |

### Device Control

| Feature | Description |
|---------|-------------|
| **Device info** | Model, iOS version, serial, UDID, Wi-Fi MAC, battery, storage |
| **Installed apps** | Full list with filters (All / User / System), search, and uninstall |
| **Screenshots** | Capture your device screen directly from the app |
| **Power controls** | Restart, shutdown, or sleep your device remotely |
| **Storage monitor** | Visual progress bar with used/total breakdown |

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

### Apps Browser

| Feature | Description |
|---------|-------------|
| **App list** | All apps from the backup with name, bundle ID, and version |
| **File tree** | Expandable directory tree for each app's data |
| **Export** | Export individual app data for analysis |

### Security & Privacy

- Decrypted database temp files are **securely zeroed out** and deleted on exit
- All backup modifications create **incremental safety backups** before any change
- **Path traversal protection** on file extraction
- **No internet connection** — the app is fully offline by design
- **No data collection** — zero telemetry, zero analytics

---

## Screenshots

### Backup Information
![Backup Information](docs/Itunes1.png)

### Backup Browser
![Backup Browser](docs/Itunes2.png)

### Media Gallery
![Media Gallery](docs/Itunes3.png)

### Apps
![Apps](docs/Itunes4.png)

### Device Info & Controls
![Device Info](docs/Itunes6.png)

![Installed Apps](docs/Itunes7.png)

### Screen Mirroring
![Screen Mirroring](docs/Itunes5.png)

---

## Installation

### Debian / Ubuntu (recommended for Linux)

```bash
sudo apt install ./path/to/iDevice-Toolkit_debian_x64.deb
```

### Fat JAR (any platform)

```bash
java -jar iDevice-Toolkit-2.0.jar
```

> Requires **Java 18+** with JavaFX.

### Optional Dependencies

These are only needed for the **Device** and **Mirror** tabs:

| Dependency | Purpose |
|------------|---------|
| [libimobiledevice](https://libimobiledevice.org/) | Device info, app listing, screenshots, power controls (USB) |
| [pymobiledevice3](https://github.com/doronz88/pymobiledevice3) | AirPlay screen mirroring and iOS 17+ tunnel (auto-installed via in-app wizard) |

The app works fully for backup browsing, media gallery, and file management without these dependencies.

---

## Building from Source

**Requirements:** JDK 18+ and Apache Maven.

```bash
# Native executable + installer
mvn clean package

# Fat JAR (cross-platform)
mvn clean compile assembly:single

# Multi-platform JAR (Windows + Linux + ARM macOS)
mvn clean compile assembly:single -Pmost_platforms

# Run directly (development)
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
| Screen Mirroring | pymobiledevice3 (Python, AirPlay + USB) |
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

## Origin

iDevice Toolkit started as a fork of [iTunes Backup Explorer](https://github.com/MaxiHuHe04/iTunes-Backup-Explorer) by [MaxiHuHe04](https://github.com/MaxiHuHe04), which provided the foundation for encrypted backup browsing. This project extends it into a full device management toolkit with AirPlay mirroring, media gallery, live device control, and a modern UI.

**Key references:**
- [iPhone Data Protection in Depth](https://conference.hitb.org/hitbsecconf2011ams/materials/D2T2) (HITB SecConf 2011)
- [iphone-dataprotection](https://code.google.com/archive/p/iphone-dataprotection/) project
- [Forensic Analysis of iTunes Backups](http://www.farleyforensics.com/2019/04/14/forensic-analysis-of-itunes-backups/) by Jack Farley
- Apple iOS Security Guide

---

## License

MIT — use it, fork it, build on it.
