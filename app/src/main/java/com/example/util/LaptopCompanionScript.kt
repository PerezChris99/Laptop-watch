package com.example.util

/**
 * DEPRECATED: no executable PIN/HTTP companion code is embedded in Android.
 *
 * The former one-click Windows BAT/Python script used an unpaired PIN-based
 * Flask server and made unsupported Defender/sleep-mode claims. It must not be
 * supplied or executed in commercial builds. Install the signed Tauri desktop
 * companion and enroll Android with the explicit Ed25519/TLS QR protocol.
 *
 * These compatibility fields deliberately fail closed and exist only to avoid
 * breaking obsolete UI imports while migrating earlier app versions.
 */
@Deprecated("Legacy PIN/HTTP companion disabled: use the paired HTTPS desktop")
object LaptopCompanionScript {
    val WINDOWS_DEFENDER_NOTES =
        "No anti-malware false-positive or always-on sleep guarantees. Use a signed desktop app."
    val INSTALL_WINDOWS_BAT = "@echo off\r\necho Legacy setup disabled. Use signed LaptopWatch desktop.\r\nexit /b 1\r\n"
    val UNINSTALL_WINDOWS_BAT = "@echo off\r\necho Remove the obsolete Flask daemon manually and uninstall old startup entries.\r\nexit /b 1\r\n"
    val PYTHON_SCRIPT =
        "raise SystemExit('Insecure legacy HTTP/PIN companion retired; install paired HTTPS desktop')"
}
