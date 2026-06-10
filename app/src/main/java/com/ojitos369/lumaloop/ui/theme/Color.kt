package com.ojitos369.lumaloop.ui.theme

import androidx.compose.ui.graphics.Color

// Neumorphic dark palette.
// Core rule of neumorphism: background and surfaces share one base color;
// depth comes from the dual light/dark shadows, not from surface tint changes.
val NeuBase = Color(0xFF24272E)        // single base for background + surfaces
val NeuBaseRaised = Color(0xFF282C34)  // subtle raised fill (cards, sheets)
val NeuBaseInset = Color(0xFF1E2127)   // concave/inset fill (inputs, tracks)

val NeuShadowDark = Color(0xCC15171B)  // bottom-right shadow
val NeuShadowLight = Color(0x14FFFFFF) // top-left highlight

val md_theme_dark_primary = Color(0xFFA78BFA)          // soft violet accent
val md_theme_dark_onPrimary = Color(0xFF1C1530)
val md_theme_dark_primaryContainer = Color(0xFF453A6B)
val md_theme_dark_onPrimaryContainer = Color(0xFFE5DCFF)

val md_theme_dark_secondary = Color(0xFF7DD3C8)        // soft teal
val md_theme_dark_onSecondary = Color(0xFF10302B)
val md_theme_dark_secondaryContainer = Color(0xFF2C4B46)
val md_theme_dark_onSecondaryContainer = Color(0xFFCBF5EE)

val md_theme_dark_tertiary = Color(0xFFF0A8B8)
val md_theme_dark_onTertiary = Color(0xFF3D1A24)
val md_theme_dark_tertiaryContainer = Color(0xFF5C3340)
val md_theme_dark_onTertiaryContainer = Color(0xFFFFDAE2)

val md_theme_dark_error = Color(0xFFF28B8B)
val md_theme_dark_onError = Color(0xFF3C1212)
val md_theme_dark_errorContainer = Color(0xFF5C2626)
val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)

val md_theme_dark_background = NeuBase
val md_theme_dark_onBackground = Color(0xFFE6E8EC)
val md_theme_dark_surface = NeuBase
val md_theme_dark_onSurface = Color(0xFFE6E8EC)
val md_theme_dark_surfaceVariant = NeuBaseRaised
val md_theme_dark_onSurfaceVariant = Color(0xFFA8ADB8)

val md_theme_dark_outline = Color(0xFF5A5F6B)
val md_theme_dark_outlineVariant = Color(0xFF353A43)
