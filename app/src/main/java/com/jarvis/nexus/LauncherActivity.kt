package com.jarvis.nexus

/**
 * JARVIS OS: la stessa app usata come schermata Home (launcher) del tablet.
 * Il componente è disattivato di default e si accende da Sistema → JARVIS OS,
 * così sul telefono il launcher non cambia.
 */
class LauncherActivity : MainActivity() {
    override val launcherMode: Boolean = true
}
