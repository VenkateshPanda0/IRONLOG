package app.ironlog.personal.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import app.ironlog.personal.domain.WeightUnit

/** The weight unit the user chose; every screen reads it to show and parse weights. */
val LocalWeightUnit = staticCompositionLocalOf { WeightUnit.KG }
