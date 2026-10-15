package dev.hearth.launcher.wallpaper

import android.graphics.Bitmap

/*
 * ZENITH 21.5 – the wallpaper gallery: many pictures, all drawn by ZENITH itself (no picture files, nothing taken from
 * anywhere), in the size of the phone's screen – and very many of the Moon. Each piece is a short recipe for the
 * brush in ArtCanvas.
 */

internal enum class ArtGroup(val label: String) {
    Moon("Mond"),
    Planets("Planeten"),
    Space("Weltraum"),
    Land("Landschaft"),
    Abstract("Abstrakt"),
    Zenith("ZENITH"),
}

/** One wallpaper: [draw] paints it. */
internal class ArtPiece(
    val id: String,
    val title: String,
    val group: ArtGroup,
    val note: String,
    val draw: ArtCanvas.() -> Unit,
)

internal object ArtCatalog {

    /** The piece as a [w] × [h] picture. (Takes from a fraction of a second up to a few seconds – call it off the main thread.) */
    fun render(piece: ArtPiece, w: Int, h: Int): Bitmap {
        val art = ArtCanvas(w, h, piece.id.hashCode() and 0xFFFF)
        piece.draw(art)
        return art.bitmap
    }

    fun of(group: ArtGroup): List<ArtPiece> = all.filter { it.group == group }

    fun find(id: String): ArtPiece? = all.firstOrNull { it.id == id }

    private val night = arrayOf(0f to 0x02040C, 0.55f to 0x0A1430, 1f to 0x1B2B52)

    val all: List<ArtPiece> = buildList {
        fun piece(id: String, title: String, group: ArtGroup, note: String, draw: ArtCanvas.() -> Unit) {
            add(ArtPiece(id, title, group, note, draw))
        }

        // -------------------------------------------------------------------------------------------------------------
        // The Moon
        // -------------------------------------------------------------------------------------------------------------
        piece("mond-voll", "Vollmond", ArtGroup.Moon, "Der volle Mond, klar und hell, mit allen Kratern und Meeren.") {
            sky(*night)
            stars(300, 0f, 0.85f)
            moon(fw * 0.5f, fh * 0.36f, fw * 0.26f, MoonLook(seed = 3), glowColor = 0xA8C0FF, glowAlpha = 0.55f)
        }
        piece("mond-riese", "Riesenmond", ArtGroup.Moon, "So nah, dass er aus dem Bild ragt – jeder Krater zu sehen.") {
            sky(0f to 0x000103, 1f to 0x05070F)
            stars(180, 0f, 1f, bright = 0.8f)
            moon(fw * 0.62f, fh * 0.5f, fw * 0.78f, MoonLook(seed = 11), glowColor = 0x8FA8E8, glowAlpha = 0.25f, glowReach = 1.5f)
        }
        piece("mond-sichel-zu", "Zunehmende Sichel", ArtGroup.Moon, "Die schmale Sichel des zunehmenden Mondes, rechts beleuchtet.") {
            sky(0f to 0x02060E, 0.6f to 0x0A2030, 1f to 0x163A4C)
            stars(280, 0f, 0.8f)
            moon(fw * 0.52f, fh * 0.34f, fw * 0.23f, MoonLook(phase = 125f, tilt = -28f, earthshine = 0.07f, seed = 5), glowColor = 0x9AD0F0, glowAlpha = 0.5f)
        }
        piece("mond-sichel-ab", "Abnehmende Sichel", ArtGroup.Moon, "Die Sichel des abnehmenden Mondes, links beleuchtet.") {
            sky(0f to 0x06040E, 0.6f to 0x1A1430, 1f to 0x2E2650)
            stars(280, 0f, 0.8f)
            moon(fw * 0.48f, fh * 0.34f, fw * 0.23f, MoonLook(phase = -125f, tilt = 28f, earthshine = 0.07f, seed = 6), glowColor = 0xC0B0F8, glowAlpha = 0.5f)
        }
        piece("mond-erdlicht", "Sichel mit Erdlicht", ArtGroup.Moon, "Die dunkle Seite schimmert im Licht der Erde.") {
            sky(*night)
            stars(320, 0f, 0.85f)
            moon(fw * 0.5f, fh * 0.37f, fw * 0.25f, MoonLook(phase = 152f, tilt = -20f, earthshine = 0.24f, seed = 7), glowColor = 0xA8C0FF, glowAlpha = 0.4f)
        }
        piece("mond-viertel-1", "Erstes Viertel", ArtGroup.Moon, "Halb beleuchtet: Krater und Berge werfen lange Schatten an der Grenze zwischen Tag und Nacht.") {
            sky(*night)
            stars(280, 0f, 0.85f)
            moon(fw * 0.5f, fh * 0.36f, fw * 0.27f, MoonLook(phase = 90f, tilt = 0f, earthshine = 0.05f, seed = 8), glowColor = 0xA8C0FF, glowAlpha = 0.45f)
        }
        piece("mond-viertel-2", "Letztes Viertel", ArtGroup.Moon, "Die andere Hälfte: links hell, rechts im Dunkeln.") {
            sky(*night)
            stars(280, 0f, 0.85f)
            moon(fw * 0.5f, fh * 0.36f, fw * 0.27f, MoonLook(phase = -90f, tilt = 0f, earthshine = 0.05f, seed = 9), glowColor = 0xA8C0FF, glowAlpha = 0.45f)
        }
        piece("mond-zunehmend", "Zunehmender Mond", ArtGroup.Moon, "Fast voll – nur ein Rand liegt noch im Schatten.") {
            sky(*night)
            stars(280, 0f, 0.85f)
            moon(fw * 0.5f, fh * 0.35f, fw * 0.27f, MoonLook(phase = 52f, tilt = -12f, earthshine = 0.04f, seed = 10), glowColor = 0xA8C0FF, glowAlpha = 0.55f)
        }
        piece("mond-abnehmend", "Abnehmender Mond", ArtGroup.Moon, "Nach dem Vollmond: der Schatten wandert von rechts herein.") {
            sky(*night)
            stars(280, 0f, 0.85f)
            moon(fw * 0.5f, fh * 0.35f, fw * 0.27f, MoonLook(phase = -52f, tilt = 12f, earthshine = 0.04f, seed = 12), glowColor = 0xA8C0FF, glowAlpha = 0.55f)
        }
        piece("mond-neu", "Neumond", ArtGroup.Moon, "Der Mond ist fast unsichtbar – dafür ist der Himmel voller Sterne und die Milchstraße steht über dem Bild.") {
            sky(0f to 0x010208, 1f to 0x060A18)
            milkyWay(fw * 0.05f, fh * 0.78f, fw * 0.95f, fh * 0.12f, fw * 0.38f)
            stars(520, 0f, 1f)
            moon(fw * 0.72f, fh * 0.64f, fw * 0.13f, MoonLook(phase = 177f, earthshine = 0.11f, seed = 13), glowAlpha = 0f)
        }
        piece("mond-blut", "Blutmond", ArtGroup.Moon, "Totale Mondfinsternis: der Mond im Schatten der Erde, kupferrot.") {
            sky(0f to 0x070204, 0.6f to 0x14060A, 1f to 0x260A0C)
            stars(220, 0f, 0.85f, bright = 0.75f, tint = 0xFFE8E0)
            moon(fw * 0.5f, fh * 0.37f, fw * 0.26f, MoonLook(eclipse = 0.95f, eclipseX = 1f, eclipseY = -0.25f, seed = 14), glowColor = 0xFF6A3A, glowAlpha = 0.30f)
        }
        piece("mond-finsternis", "Mondfinsternis", ArtGroup.Moon, "Der Schatten der Erde schiebt sich über den Mond – halb ist er schon rot.") {
            sky(0f to 0x040208, 0.6f to 0x120A1C, 1f to 0x24142C)
            stars(260, 0f, 0.85f)
            moon(fw * 0.5f, fh * 0.37f, fw * 0.26f, MoonLook(eclipse = 0.5f, eclipseX = 1f, eclipseY = -0.35f, seed = 15), glowColor = 0xFFA080, glowAlpha = 0.35f)
        }
        piece("mond-super", "Supermond", ArtGroup.Moon, "Riesig und golden über den Bergen, nah am Horizont.") {
            sky(0f to 0x070A1E, 0.45f to 0x2A2A56, 0.75f to 0x9A5A5A, 1f to 0xE0A060)
            stars(120, 0f, 0.45f, bright = 0.7f)
            moon(fw * 0.5f, fh * 0.56f, fw * 0.36f, MoonLook(tint = 0xFFE2A8, seed = 16), glowColor = 0xFFC070, glowAlpha = 0.6f, glowReach = 2.6f)
            peaks(fh * 0.82f, fh * 0.16f, 5f, 40, 0x2A1E36, 0x180F22, fh * 1.0f, 2.3f)
            peaks(fh * 0.92f, fh * 0.12f, 4f, 45, 0x120A1A, 0x06030A, fh * 1.05f, 8.1f)
        }
        piece("mond-blau", "Blue Moon", ArtGroup.Moon, "Der zweite Vollmond im Monat, in kühlem Blau.") {
            sky(0f to 0x010414, 0.5f to 0x0A2260, 1f to 0x1C4AA0)
            stars(260, 0f, 0.8f, tint = 0xDDE8FF)
            moon(fw * 0.5f, fh * 0.36f, fw * 0.26f, MoonLook(tint = 0xCFE0FF, seed = 17), glowColor = 0x5A8CFF, glowAlpha = 0.65f)
        }
        piece("mond-gold", "Goldener Mond", ArtGroup.Moon, "Ein warmer, goldener Mond in einer bernsteinfarbenen Nacht.") {
            sky(0f to 0x0A0604, 0.55f to 0x2A1A0A, 1f to 0x5A3A14)
            stars(200, 0f, 0.8f, tint = 0xFFF0D0)
            moon(fw * 0.5f, fh * 0.36f, fw * 0.26f, MoonLook(tint = 0xFFD98A, seed = 18), glowColor = 0xFFB84A, glowAlpha = 0.6f)
        }
        piece("mond-silber", "Silbermond", ArtGroup.Moon, "Kühles Silber auf Grau – ruhig und klar.") {
            sky(0f to 0x05070B, 1f to 0x161C26)
            stars(240, 0f, 0.85f)
            moon(fw * 0.5f, fh * 0.38f, fw * 0.25f, MoonLook(tint = 0xE8EEF8, seed = 19), glowColor = 0xDCE6F5, glowAlpha = 0.4f)
        }
        piece("mond-rosa", "Rosa Mond", ArtGroup.Moon, "Ein Vollmond im Rosa der Dämmerung über sanften Hügeln.") {
            sky(0f to 0x1A0A24, 0.5f to 0x6A2A5A, 1f to 0xD07A8A)
            stars(90, 0f, 0.4f, bright = 0.6f)
            moon(fw * 0.5f, fh * 0.4f, fw * 0.23f, MoonLook(tint = 0xFFD0DC, seed = 20), glowColor = 0xFFA0C0, glowAlpha = 0.55f)
            hills(fh * 0.8f, fh * 0.04f, 6f, 0.5f, 0x4A1E4A, 0x240E26)
            hills(fh * 0.9f, fh * 0.035f, 5f, 2.2f, 0x2A1030, 0x0E0512)
        }
        piece("mond-erdbeer", "Erdbeermond", ArtGroup.Moon, "Tief und orange über dem Meer, sein Licht ein Weg aus Funken.") {
            sky(0f to 0x120808, 0.5f to 0x5A2A30, 0.64f to 0xC8604A, 1f to 0xC8604A)
            stars(70, 0f, 0.35f, bright = 0.5f)
            glow(fw * 0.5f, fh * 0.62f, fw * 0.8f, 0xFF8A60, 0.4f, 2f)
            moon(fw * 0.5f, fh * 0.46f, fw * 0.22f, MoonLook(tint = 0xFFB89A, seed = 21), glowColor = 0xFF9A70, glowAlpha = 0.55f)
            sea(fh * 0.62f, 0x8A3A3A, 0x1A0A0C, fw * 0.5f, fw * 0.22f, light = 0xFFC0A0, strength = 0.85f)
        }
        piece("mond-jaeger", "Jägermond", ArtGroup.Moon, "Ein großer, orangeroter Mond hinter schwarzen Tannen.") {
            sky(0f to 0x0A0604, 0.5f to 0x2A1408, 1f to 0x7A3A14)
            stars(120, 0f, 0.5f, bright = 0.6f, tint = 0xFFE8D0)
            moon(fw * 0.5f, fh * 0.42f, fw * 0.28f, MoonLook(tint = 0xFFB066, seed = 22), glowColor = 0xFF8A3A, glowAlpha = 0.55f)
            pines(0x2A1A10, fh * 0.80f, 0.06f, 0.11f, 28, 8)
            fogBand(fh * 0.74f, fh * 0.84f, 0x8A4A20, 0f, 0.25f)
            pines(0x140A06, fh * 0.9f, 0.10f, 0.18f, 20, 9)
            pines(0x060302, fh * 1.0f, 0.15f, 0.28f, 12, 10)
        }
        piece("mond-schnee", "Schneemond", ArtGroup.Moon, "Ein blasser Mond über einer verschneiten Ebene.") {
            sky(0f to 0x050A14, 0.55f to 0x16283C, 1f to 0x3A5470)
            stars(240, 0f, 0.7f)
            moon(fw * 0.5f, fh * 0.34f, fw * 0.23f, MoonLook(tint = 0xEAF4FF, seed = 23), glowColor = 0xBFE0FF, glowAlpha = 0.5f)
            hills(fh * 0.74f, fh * 0.03f, 5f, 0.6f, 0x9CB4D0, 0x4A6688, light = 0xEAF4FF, lightAlpha = 0.5f)
            hills(fh * 0.86f, fh * 0.04f, 4f, 2.9f, 0xCADAEA, 0x5A7494, light = 0xFFFFFF, lightAlpha = 0.55f)
            pines(0x0E1A2A, fh * 0.80f, 0.04f, 0.08f, 14, 31)
        }
        piece("mond-wolf", "Wolfsmond", ArtGroup.Moon, "Ein riesiger Mond hinter dem dunklen Wald, in dem die Wölfe heulen.") {
            sky(0f to 0x020409, 0.5f to 0x0A1A24, 0.85f to 0x1C3A44, 1f to 0x1C3A44)
            stars(200, 0f, 0.55f)
            moon(fw * 0.5f, fh * 0.3f, fw * 0.3f, MoonLook(tint = 0xFFFFF2, seed = 24), glowColor = 0xA8D8E8, glowAlpha = 0.5f)
            pines(0x1A3038, fh * 0.80f, 0.07f, 0.13f, 26, 8)
            fogBand(fh * 0.75f, fh * 0.84f, 0x2A5560, 0f, 0.22f)
            pines(0x102129, fh * 0.88f, 0.10f, 0.19f, 20, 9)
            fogBand(fh * 0.83f, fh * 0.92f, 0x2A5560, 0f, 0.2f)
            pines(0x060C10, fh * 1.0f, 0.16f, 0.30f, 12, 10)
        }
        piece("mond-meer", "Mond über dem Meer", ArtGroup.Moon, "Der Mond über dem Horizont, sein Licht ein Pfad auf dem Wasser.") {
            sky(0f to 0x040814, 0.45f to 0x0F1D3F, 0.62f to 0x35527F, 1f to 0x35527F)
            stars(220, 0f, 0.5f)
            glow(fw * 0.5f, fh * 0.62f, fw * 0.7f, 0x6F95D8, 0.35f, 2f)
            moon(fw * 0.5f, fh * 0.34f, fw * 0.15f, MoonLook(seed = 25), glowColor = 0x9DB8F5, glowAlpha = 0.5f)
            sea(fh * 0.62f, 0x2B4670, 0x050A16, fw * 0.5f, fw * 0.15f)
        }
        piece("mond-berge", "Mond über den Bergen", ArtGroup.Moon, "Bergketten hintereinander im Dunst, der Mond darüber.") {
            sky(0f to 0x02040C, 0.5f to 0x0B1636, 0.75f to 0x233E70, 1f to 0x2F4F86)
            stars(240, 0f, 0.62f)
            moon(fw * 0.64f, fh * 0.25f, fw * 0.16f, MoonLook(seed = 26), glowColor = 0xA8C0FF, glowAlpha = 0.55f)
            peaks(fh * 0.60f, fh * 0.20f, 6f, 40, 0x4A6396, 0x2C426F, fh * 0.80f, 0f)
            fogBand(fh * 0.58f, fh * 0.70f, 0x4B6EA8, 0f, 0.34f)
            peaks(fh * 0.70f, fh * 0.18f, 5f, 41, 0x2D4574, 0x1B2A4C, fh * 0.90f, 7.3f)
            fogBand(fh * 0.68f, fh * 0.80f, 0x4B6EA8, 0f, 0.30f)
            peaks(fh * 0.80f, fh * 0.15f, 4f, 42, 0x1A2A50, 0x0F1932, fh * 1.0f, 14.6f)
            fogBand(fh * 0.78f, fh * 0.90f, 0x4B6EA8, 0f, 0.24f)
            peaks(fh * 0.90f, fh * 0.12f, 3f, 43, 0x0B1226, 0x05080F, fh * 1.1f, 21.9f)
        }
        piece("mond-stadt", "Mond über der Stadt", ArtGroup.Moon, "Die Skyline mit erleuchteten Fenstern, darüber ein blasser Halbmond.") {
            sky(0f to 0x05060F, 0.55f to 0x161B38, 0.78f to 0x3A2F55, 1f to 0x5A3B5E)
            stars(150, 0f, 0.5f, bright = 0.8f)
            moon(fw * 0.7f, fh * 0.28f, fw * 0.13f, MoonLook(phase = 25f, tilt = 10f, tint = 0xFFF7E6, seed = 27), glowColor = 0xE6C8FF, glowAlpha = 0.45f)
            city(0x1E1E3C, fh * 0.86f, 0.06f, 0.22f, fw * 0.072f, 0.08f, seedOffset = 2)
            fogBand(fh * 0.80f, fh * 0.86f, 0x6A4A80, 0f, 0.18f)
            city(0x0E0E22, fh * 0.94f, 0.07f, 0.30f, fw * 0.094f, 0.14f, seedOffset = 3)
            fogBand(fh * 0.88f, fh * 0.94f, 0x6A4A80, 0f, 0.18f)
            city(0x05050E, fh * 1.02f, 0.08f, 0.20f, fw * 0.128f, 0.12f, seedOffset = 4)
        }
        piece("mond-duenen", "Mond in den Dünen", ArtGroup.Moon, "Ein Halbmond über der Wüste, die Dünen warm im Zwielicht.") {
            sky(0f to 0x040712, 0.45f to 0x142446, 0.7f to 0x5A5A82, 1f to 0x8A6A78)
            stars(170, 0f, 0.45f)
            moon(fw * 0.35f, fh * 0.3f, fw * 0.14f, MoonLook(phase = 90f, tilt = -20f, tint = 0xFFF5E6, seed = 28), glowColor = 0xE0C8E8, glowAlpha = 0.4f)
            hills(fh * 0.70f, fh * 0.06f, 2.6f, 1.3f, 0x4A4868, 0x2E2C48, light = 0xC8B0C8, lightAlpha = 0.3f)
            hills(fh * 0.78f, fh * 0.06f, 3.6f, 2.6f, 0x3A3858, 0x1C1B30, light = 0xA890B0, lightAlpha = 0.3f)
            hills(fh * 0.88f, fh * 0.06f, 4.6f, 3.9f, 0x272642, 0x0D0D1C, light = 0x8A7AA0, lightAlpha = 0.3f)
        }
        piece("mond-wolken", "Mond hinter Wolken", ArtGroup.Moon, "Wolken ziehen vor dem Mond vorbei und fangen sein Licht.") {
            sky(0f to 0x03060F, 0.6f to 0x0D1B3A, 1f to 0x22385E)
            stars(220, 0f, 0.6f, bright = 0.8f)
            moon(fw * 0.45f, fh * 0.36f, fw * 0.2f, MoonLook(seed = 29), glowColor = 0xB0C6FF, glowAlpha = 0.6f)
            clouds(46, fh * 0.28f, fh * 0.62f, 0x142038, 0x8EA6D6, fw * 0.45f, fh * 0.36f, fw * 0.4f, seedOffset = 3)
            clouds(20, fh * 0.65f, fh * 0.95f, 0x0C1428, 0x4A6090, fw * 0.45f, fh * 0.36f, fw * 0.9f, alphaMin = 0.35f, seedOffset = 4)
        }
        piece("mond-hof", "Mond mit Hof", ArtGroup.Moon, "Eisige Wolken hoch oben legen einen Ring aus Licht um den Mond.") {
            sky(0f to 0x03060F, 0.6f to 0x0C1A36, 1f to 0x1C3058)
            stars(120, 0f, 0.8f, bright = 0.6f)
            moon(fw * 0.5f, fh * 0.38f, fw * 0.16f, MoonLook(seed = 30), glowColor = 0xC0D4FF, glowAlpha = 0.6f, halo = true)
            clouds(14, fh * 0.2f, fh * 0.55f, 0x16243E, 0x6A80B0, fw * 0.5f, fh * 0.38f, fw * 0.5f, alphaMin = 0.12f, alphaVar = 0.12f, widthMin = 0.25f, widthVar = 0.35f, seedOffset = 6)
        }
        piece("mond-see", "Mond im See", ArtGroup.Moon, "Ein stiller See spiegelt den Mond und die Berge.") {
            sky(0f to 0x03060F, 0.4f to 0x0B1636, 0.6f to 0x2A4678, 1f to 0x2A4678)
            stars(230, 0f, 0.5f)
            moon(fw * 0.5f, fh * 0.28f, fw * 0.17f, MoonLook(seed = 31), glowColor = 0xA8C0FF, glowAlpha = 0.55f)
            peaks(fh * 0.55f, fh * 0.13f, 6f, 50, 0x22345C, 0x16213F, fh * 0.62f, 3f)
            peaks(fh * 0.60f, fh * 0.09f, 5f, 52, 0x101A33, 0x0A1124, fh * 0.64f, 9f)
            lake(fh * 0.60f, 0x0A1428, 0.45f)
        }
        piece("mond-baum", "Kahler Baum", ArtGroup.Moon, "Ein kahler Baum zeichnet seine Äste vor den Vollmond.") {
            sky(0f to 0x03050C, 0.6f to 0x0E1A34, 1f to 0x24365A)
            stars(220, 0f, 0.7f)
            moon(fw * 0.66f, fh * 0.3f, fw * 0.24f, MoonLook(seed = 32), glowColor = 0xB0C8FF, glowAlpha = 0.6f)
            hills(fh * 0.88f, fh * 0.03f, 4f, 0.8f, 0x0C1426, 0x05080F)
            bareTree(fw * 0.26f, fh * 0.90f, fh * 0.17f, 0x03050A, depth = 9, spread = 0.52f, seedOffset = 5)
        }
        piece("mond-aufgang", "Mondaufgang", ArtGroup.Moon, "Der Mond steigt hinter den Bergen auf, orange im Dunst der Dämmerung.") {
            sky(0f to 0x0A0A22, 0.5f to 0x3A2A56, 0.78f to 0xB0584E, 1f to 0xE69A5C)
            stars(100, 0f, 0.4f, bright = 0.7f)
            moon(fw * 0.5f, fh * 0.70f, fw * 0.27f, MoonLook(tint = 0xFFC890, seed = 33), glowColor = 0xFFA060, glowAlpha = 0.6f, glowReach = 2.8f)
            peaks(fh * 0.80f, fh * 0.12f, 5f, 60, 0x4A2E4C, 0x2A1830, fh * 0.9f, 1.4f)
            fogBand(fh * 0.76f, fh * 0.86f, 0xE69A5C, 0f, 0.25f)
            peaks(fh * 0.90f, fh * 0.10f, 4f, 61, 0x1C0E22, 0x0A0510, fh * 1.0f, 6.2f)
        }
        piece("mond-erdaufgang", "Erdaufgang", ArtGroup.Moon, "Vom Mond aus gesehen: die Erde über dem grauen Horizont, kein Wind, kein Himmel.") {
            sky(0f to 0x000000, 1f to 0x02030A)
            stars(420, 0f, 0.85f)
            planet(Planet.Earth, fw * 0.36f, fh * 0.25f, fw * 0.2f, phase = 40f, tilt = 15f, glowColor = 0x5AA0FF, glowAlpha = 0.3f)
            lunarGround(fh * 0.80f)
        }
        piece("mond-krater", "Krater", ArtGroup.Moon, "Die Mondoberfläche aus der Nähe: Krater, Wälle und lange Schatten.") {
            sky(0f to 0x000000, 1f to 0x000000)
            moonSurface(MoonLook(phase = 72f, tilt = -30f, seed = 41, zoom = 0.36f, offsetX = 0.2f, offsetY = -0.1f, craters = 520, craterSize = 3.2f, relief = 1.1f))
        }
        piece("mond-venus", "Mond und Venus", ArtGroup.Moon, "Die schmale Mondsichel und die Venus, hell wie ein Stern, in der Abenddämmerung.") {
            sky(0f to 0x070A1E, 0.55f to 0x1C2A52, 0.85f to 0x6A5A7E, 1f to 0xC88A6A)
            stars(110, 0f, 0.5f, bright = 0.7f)
            moon(fw * 0.64f, fh * 0.3f, fw * 0.15f, MoonLook(phase = 140f, tilt = -30f, earthshine = 0.16f, seed = 34), glowColor = 0xFFE0C0, glowAlpha = 0.4f)
            glow(fw * 0.28f, fh * 0.42f, fw * 0.16f, 0xFFF4D8, 0.55f, 2f)
            glow(fw * 0.28f, fh * 0.42f, fw * 0.05f, 0xFFFFFF, 0.9f, 1.4f)
            hills(fh * 0.90f, fh * 0.03f, 4f, 0.3f, 0x1A1424, 0x08060C)
        }
        piece("mond-minimal", "Minimal-Mond", ArtGroup.Moon, "Nur ein Mond und ein Verlauf – ruhig und klar.") {
            sky(0f to 0x0E1030, 0.6f to 0x24285A, 1f to 0x4A4A8A)
            flatMoon(fw * 0.5f, fh * 0.4f, fw * 0.2f, 0xF4EEDD, carve = 0x141844, shift = 0.55f)
        }
        piece("mond-wald", "Mondlicht im Wald", ArtGroup.Moon, "Das Licht fällt in Strahlen durch die Tannen, am Boden liegt Nebel.") {
            sky(0f to 0x020609, 0.5f to 0x0A1F26, 1f to 0x1C4048)
            stars(130, 0f, 0.4f, bright = 0.7f)
            moon(fw * 0.5f, fh * 0.2f, fw * 0.17f, MoonLook(seed = 35), glowColor = 0x9ADCE8, glowAlpha = 0.55f)
            lightShafts(fw * 0.5f, fh * 0.2f, 9, fh * 0.75f, 0xBFEFF4, 0.20f)
            pines(0x143038, fh * 0.78f, 0.07f, 0.13f, 24, 8)
            fogBand(fh * 0.72f, fh * 0.84f, 0x4A8890, 0f, 0.30f)
            pines(0x0A1E24, fh * 0.88f, 0.11f, 0.20f, 18, 9)
            fogBand(fh * 0.82f, fh * 0.94f, 0x4A8890, 0f, 0.28f)
            pines(0x040C10, fh * 1.0f, 0.18f, 0.32f, 11, 10)
        }
        piece("mond-aurora", "Polarlicht und Mond", ArtGroup.Moon, "Grüne Vorhänge aus Licht, eine Mondsichel und der Schnee darunter.") {
            sky(0f to 0x020510, 0.5f to 0x071A2E, 0.8f to 0x0E2D3C, 1f to 0x16344A)
            stars(240, 0f, 0.7f)
            aurora(0.26f, listOf(0x35F2A0 to 0.55f, 0x3AD9E8 to 0.30f, 0x9A6BFF to 0.18f))
            moon(fw * 0.74f, fh * 0.14f, fw * 0.11f, MoonLook(phase = 140f, tilt = -28f, earthshine = 0.2f, seed = 36), glowColor = 0x9BC6FF, glowAlpha = 0.4f)
            peaks(fh * 0.76f, fh * 0.09f, 3.5f, 70, 0x14283A, 0x0B1826, fh * 0.9f, 1.5f)
            hills(fh * 0.84f, fh * 0.02f, 3f, 0.4f, 0x9FB8D0, 0x1A2C44)
        }
        piece("mond-zenith", "ZENITH-Mond", ArtGroup.Moon, "Ein Mond im Grün von ZENITH, Ringe aus Licht um ihn.") {
            sky(0f to 0x010305, 0.6f to 0x04120F, 1f to 0x082018)
            stars(200, 0f, 0.9f, tint = 0xDFFFEF)
            ringsArt(fw * 0.5f, fh * 0.4f, 7, fw * 0.3f, fw * 0.075f, 0x39E88A, 0.5f)
            moon(fw * 0.5f, fh * 0.4f, fw * 0.22f, MoonLook(tint = 0xEAFFF2, seed = 37), glowColor = 0x39E88A, glowAlpha = 0.5f)
        }
        piece("mond-doppel", "Doppelmond", ArtGroup.Moon, "Zwei Monde am Himmel eines fernen Planeten.") {
            sky(0f to 0x050818, 0.55f to 0x1A1648, 1f to 0x3A2A6A)
            stars(260, 0f, 0.8f)
            moon(fw * 0.36f, fh * 0.3f, fw * 0.24f, MoonLook(seed = 38), glowColor = 0xB0B8FF, glowAlpha = 0.5f)
            moon(fw * 0.76f, fh * 0.56f, fw * 0.11f, MoonLook(phase = 60f, tilt = -20f, tint = 0xBFD6FF, seed = 39), glowColor = 0x7AA0FF, glowAlpha = 0.5f)
        }
        piece("mond-phasen", "Mondphasen", ArtGroup.Moon, "Alle acht Phasen: von Neumond über Halbmond und Vollmond zurück.") {
            sky(0f to 0x02040C, 0.6f to 0x080E24, 1f to 0x101C3C)
            stars(200, 0f, 1f, bright = 0.7f)
            val phases = floatArrayOf(178f, 135f, 90f, 45f, 0f, -45f, -90f, -135f)
            for (i in phases.indices) {
                val col = i % 2
                val row = i / 2
                val cx = fw * (0.30f + 0.40f * col)
                val cy = fh * (0.16f + 0.225f * row)
                moon(cx, cy, fw * 0.155f, MoonLook(phase = phases[i], earthshine = 0.08f, seed = 3), glowColor = 0xA8C0FF, glowAlpha = 0.3f, glowReach = 2f)
            }
        }
        piece("mond-saturn", "Mond und Saturn", ArtGroup.Moon, "Die Mondsichel und der Ringplanet, ein seltener Moment am Nachthimmel.") {
            sky(*night)
            stars(300, 0f, 0.9f)
            planet(Planet.Saturn, fw * 0.3f, fh * 0.6f, fw * 0.11f, phase = 35f, tilt = 20f, glowColor = 0xE8D2A0, glowAlpha = 0.2f)
            moon(fw * 0.62f, fh * 0.3f, fw * 0.21f, MoonLook(phase = 128f, tilt = -25f, earthshine = 0.1f, seed = 40), glowColor = 0xA8C0FF, glowAlpha = 0.5f)
        }

        // -------------------------------------------------------------------------------------------------------------
        // Planets
        // -------------------------------------------------------------------------------------------------------------
        fun planetScene(id: String, title: String, note: String, kind: Planet, r: Float, phase: Float, tilt: Float, glowColor: Int, glowAlpha: Float, cy: Float = 0.42f) {
            piece(id, title, ArtGroup.Planets, note) {
                sky(0f to 0x010207, 1f to 0x050914)
                stars(320, 0f, 1f)
                planet(kind, fw * 0.5f, fh * cy, fw * r, phase, tilt, glowColor, glowAlpha)
            }
        }
        planetScene("planet-saturn", "Saturn", "Der Ringplanet, groß im Bild – die Ringe fangen das Licht.", Planet.Saturn, 0.27f, 35f, 20f, 0xE8D2A0, 0.30f)
        planetScene("planet-jupiter", "Jupiter", "Streifen, Wirbel und der große rote Fleck.", Planet.Jupiter, 0.38f, 40f, 15f, 0xE8C8A0, 0.30f)
        planetScene("planet-erde", "Erde", "Der blaue Planet mit Kontinenten, Wolken und einem Rand aus Licht.", Planet.Earth, 0.36f, 30f, 15f, 0x4AA3FF, 0.40f)
        planetScene("planet-mars", "Mars", "Rostrot, mit dunklen Meeren und einer weißen Polkappe.", Planet.Mars, 0.32f, 35f, 10f, 0xE8905A, 0.30f)
        planetScene("planet-venus", "Venus", "Der Morgenstern unter seinen dichten, gelben Wolken.", Planet.Venus, 0.34f, 40f, 10f, 0xF5DDA0, 0.30f)
        planetScene("planet-neptun", "Neptun", "Tiefblau, mit Bändern und einem dunklen Sturm.", Planet.Neptune, 0.34f, 40f, 10f, 0x5A8CFF, 0.30f)
        piece("planet-erde-mond", "Erde und Mond", ArtGroup.Planets, "Die Erde groß im Vordergrund, der Mond klein in der Ferne.") {
            sky(0f to 0x010207, 1f to 0x050914)
            stars(320, 0f, 1f)
            planet(Planet.Earth, fw * 0.38f, fh * 0.58f, fw * 0.42f, phase = 40f, tilt = 15f, glowColor = 0x4AA3FF, glowAlpha = 0.4f)
            moon(fw * 0.82f, fh * 0.2f, fw * 0.085f, MoonLook(phase = 38f, tilt = 10f, seed = 42), glowColor = 0xBFD0FF, glowAlpha = 0.3f, glowReach = 2f)
        }

        // -------------------------------------------------------------------------------------------------------------
        // Space
        // -------------------------------------------------------------------------------------------------------------
        fun nebulaScene(id: String, title: String, note: String, c1: Int, c2: Int, noiseSeed: Int, cover: Float) {
            piece(id, title, ArtGroup.Space, note) {
                sky(0f to 0x02020A, 1f to 0x04050F)
                nebula(c1, c2, noiseSeed, cover)
                stars(400, 0f, 1f)
            }
        }
        nebulaScene("raum-nebel-rosa", "Nebel in Rosa", "Eine Wolke aus Gas und Staub in Rosa und Violett.", 0xE0408A, 0x4A5CFF, 5, 0.50f)
        nebulaScene("raum-nebel-blau", "Nebel in Blau", "Türkis und tiefes Blau, durchzogen von dunklem Staub.", 0x1E78FF, 0x38E0C8, 8, 0.52f)
        nebulaScene("raum-nebel-feuer", "Feuernebel", "Glühendes Orange und Rot, wie ein Stern, der gerade explodiert ist.", 0xFF6A20, 0xE02050, 14, 0.50f)
        nebulaScene("raum-nebel-gold", "Goldnebel", "Gold und Violett – ein Nebel, in dem Sterne entstehen.", 0xFFC040, 0x9040FF, 21, 0.50f)
        piece("raum-galaxie", "Spiralgalaxie", ArtGroup.Space, "Eine Galaxie mit zwei Armen, von der Seite gesehen.") {
            sky(0f to 0x010208, 1f to 0x040612)
            stars(420, 0f, 1f, bright = 0.9f)
            galaxy(fw * 0.5f, fh * 0.44f, fw * 0.62f, -24f, 0.42f, 0xFFE6B0, 0x6A8CFF)
        }
        piece("raum-milchstrasse", "Milchstraße", ArtGroup.Space, "Das Band der Milchstraße über dem dunklen Wald.") {
            sky(0f to 0x010208, 0.7f to 0x050A1A, 1f to 0x0C1830)
            milkyWay(fw * 0.02f, fh * 0.80f, fw * 0.98f, fh * 0.10f, fw * 0.40f)
            stars(480, 0f, 0.9f)
            pines(0x050A10, fh * 0.97f, 0.05f, 0.10f, 30, 8)
            pines(0x020407, fh * 1.04f, 0.09f, 0.17f, 16, 9)
        }
        piece("raum-loch", "Schwarzes Loch", ArtGroup.Space, "Die Scheibe aus glühendem Gas, und über dem Loch der Ring aus gebogenem Licht.") {
            sky(0f to 0x010103, 1f to 0x050408)
            stars(300, 0f, 1f, bright = 0.8f)
            blackHole(fw * 0.5f, fh * 0.44f, fw * 0.11f)
        }
        piece("raum-komet", "Komet", ArtGroup.Space, "Ein Komet mit seinem blauen Gas- und seinem gelben Staubschweif.") {
            sky(0f to 0x01030A, 1f to 0x060A1C)
            stars(360, 0f, 1f)
            comet(fw * 0.3f, fh * 0.66f, fw * 0.95f, fh * 0.12f, fw * 0.022f)
        }
        piece("raum-sterne", "Sternenfeld", ArtGroup.Space, "Tausend Sterne in der Dunkelheit.") {
            sky(0f to 0x000002, 1f to 0x03040C)
            glow(fw * 0.3f, fh * 0.3f, fw * 0.7f, 0x3A5AC8, 0.12f, 2f)
            glow(fw * 0.8f, fh * 0.7f, fw * 0.8f, 0x8A3AA8, 0.10f, 2f)
            stars(1500, 0f, 1f, size = 0.9f)
            stars(60, 0f, 1f, size = 1.8f, seedOffset = 7)
        }

        // -------------------------------------------------------------------------------------------------------------
        // Landscapes
        // -------------------------------------------------------------------------------------------------------------
        piece("land-duenen", "Dünen im Abendlicht", ArtGroup.Land, "Die Sonne sinkt hinter die Wüste, die Dünen glühen.") {
            sky(0f to 0x1A0E2E, 0.45f to 0x6A2E5A, 0.72f to 0xE0705A, 1f to 0xF5A860)
            sun(fw * 0.5f, fh * 0.64f, fw * 0.08f, 0xFFD890, 0xFF9A50)
            hills(fh * 0.70f, fh * 0.05f, 2.4f, 0.9f, 0x8A3A4A, 0x4A1E34, light = 0xFFB070, lightAlpha = 0.55f)
            hills(fh * 0.79f, fh * 0.055f, 3.2f, 2.4f, 0x5E2A44, 0x2A1228, light = 0xE88A60, lightAlpha = 0.5f)
            hills(fh * 0.90f, fh * 0.05f, 4.4f, 4.1f, 0x2E1632, 0x0E0610, light = 0xB05A58, lightAlpha = 0.4f)
        }
        piece("land-berge-nebel", "Berge im Morgennebel", ArtGroup.Land, "Gipfel hintereinander im hellen Dunst des Morgens.") {
            sky(0f to 0x2A3A66, 0.45f to 0x7A8CB8, 0.75f to 0xE8B8A8, 1f to 0xF8DCC0)
            sun(fw * 0.7f, fh * 0.42f, fw * 0.05f, 0xFFF0D0, 0xFFD8A8, 0.5f)
            peaks(fh * 0.62f, fh * 0.20f, 5f, 80, 0x8A8CB8, 0x6A74A0, fh * 0.8f, 0f)
            fogBand(fh * 0.58f, fh * 0.72f, 0xF0D8D0, 0f, 0.55f)
            peaks(fh * 0.74f, fh * 0.18f, 4.4f, 81, 0x626C9A, 0x465280, fh * 0.9f, 5f)
            fogBand(fh * 0.70f, fh * 0.84f, 0xE8D0CC, 0f, 0.50f)
            peaks(fh * 0.86f, fh * 0.15f, 3.8f, 82, 0x3A4472, 0x242C54, fh * 1.0f, 10f)
            fogBand(fh * 0.82f, fh * 0.94f, 0xE0C8C8, 0f, 0.45f)
            peaks(fh * 0.98f, fh * 0.12f, 3f, 83, 0x1C2244, 0x0E1228, fh * 1.1f, 15f)
        }
        piece("land-meer-nacht", "Meer bei Nacht", ArtGroup.Land, "Die Milchstraße über dem ruhigen Meer.") {
            sky(0f to 0x010208, 0.55f to 0x08122A, 0.66f to 0x1C3560, 1f to 0x1C3560)
            milkyWay(fw * 0.1f, fh * 0.62f, fw * 0.9f, fh * 0.06f, fw * 0.34f)
            stars(420, 0f, 0.62f)
            glow(fw * 0.5f, fh * 0.66f, fw * 0.8f, 0x4A78C8, 0.30f, 2f)
            sea(fh * 0.66f, 0x16284A, 0x03060F, fw * 0.5f, fw * 0.1f, light = 0xA8C0FF, strength = 0.35f)
        }
        piece("land-polarlicht", "Polarlicht", ArtGroup.Land, "Das Nordlicht über einem zugefrorenen See und dunklen Tannen.") {
            sky(0f to 0x020510, 0.5f to 0x061C30, 0.8f to 0x0C3040, 1f to 0x12404E)
            stars(260, 0f, 0.7f)
            aurora(0.2f, listOf(0x35F2A0 to 0.62f, 0x3AD9E8 to 0.34f, 0x9A6BFF to 0.22f))
            hills(fh * 0.80f, fh * 0.015f, 3f, 0.4f, 0x2A5468, 0x0E2230)
            pines(0x040A10, fh * 0.80f, 0.05f, 0.10f, 24, 8)
            pines(0x020508, fh * 0.92f, 0.09f, 0.18f, 14, 9)
        }
        piece("land-wald-nebel", "Wald im Nebel", ArtGroup.Land, "Tannen in Reihen, jede hinter mehr Nebel.") {
            sky(0f to 0x1C3A34, 0.5f to 0x4A7A70, 1f to 0x9CC4B4)
            sun(fw * 0.5f, fh * 0.3f, fw * 0.07f, 0xFFF4D8, 0xE0F0D0, 0.55f)
            pines(0x5A8A7E, fh * 0.66f, 0.05f, 0.10f, 30, 8)
            fogBand(fh * 0.6f, fh * 0.72f, 0xCCE4D8, 0f, 0.62f)
            pines(0x3E6A60, fh * 0.76f, 0.08f, 0.15f, 24, 9)
            fogBand(fh * 0.70f, fh * 0.82f, 0xB8D8CC, 0f, 0.55f)
            pines(0x264A44, fh * 0.88f, 0.12f, 0.22f, 18, 10)
            fogBand(fh * 0.82f, fh * 0.94f, 0x9CC0B4, 0f, 0.5f)
            pines(0x0E2220, fh * 1.0f, 0.18f, 0.32f, 11, 11)
        }
        piece("land-wolkenmeer", "Wolkenmeer bei Sonnenuntergang", ArtGroup.Land, "Über den Wolken, die Sonne taucht sie in Gold und Violett.") {
            sky(0f to 0x1A1240, 0.4f to 0x6A3A7A, 0.7f to 0xF07A5A, 1f to 0xFFC070)
            sun(fw * 0.5f, fh * 0.66f, fw * 0.075f, 0xFFE0A0, 0xFF9A50)
            clouds(50, fh * 0.62f, fh * 0.98f, 0x5A2E6E, 0xFFB070, fw * 0.5f, fh * 0.66f, fw * 0.6f, alphaMin = 0.45f, alphaVar = 0.4f, widthMin = 0.2f, widthVar = 0.4f, seedOffset = 3)
            clouds(30, fh * 0.80f, fh * 1.05f, 0x2E1A4A, 0xC8607A, fw * 0.5f, fh * 0.66f, fw * 0.9f, alphaMin = 0.55f, alphaVar = 0.3f, widthMin = 0.25f, widthVar = 0.4f, seedOffset = 4)
        }
        piece("land-gebirge-morgen", "Gebirge bei Sonnenaufgang", ArtGroup.Land, "Der erste Strahl trifft die Gipfel.") {
            sky(0f to 0x0C1640, 0.4f to 0x4A5A9A, 0.7f to 0xF0987A, 1f to 0xFFD8A0)
            sun(fw * 0.5f, fh * 0.60f, fw * 0.06f, 0xFFF4D0, 0xFFC890, 0.6f)
            peaks(fh * 0.66f, fh * 0.22f, 4.5f, 90, 0xC8786A, 0x7A4A6A, fh * 0.86f, 2f)
            fogBand(fh * 0.64f, fh * 0.78f, 0xFFC8A0, 0f, 0.4f)
            peaks(fh * 0.80f, fh * 0.18f, 4f, 91, 0x7A4A6A, 0x3A2450, fh * 0.96f, 8f)
            fogBand(fh * 0.78f, fh * 0.90f, 0xE8A890, 0f, 0.35f)
            peaks(fh * 0.94f, fh * 0.14f, 3f, 92, 0x2A1838, 0x120A1E, fh * 1.1f, 14f)
        }

        // -------------------------------------------------------------------------------------------------------------
        // Abstract
        // -------------------------------------------------------------------------------------------------------------
        piece("abstrakt-wellen", "Wellen", ArtGroup.Abstract, "Weiche Wellen in Violett, Rosa und Blau.") {
            sky(0f to 0x120A36, 0.6f to 0x2A1458, 1f to 0x4A1E6A)
            waves(7, fh * 0.46f, fh * 0.075f, fh * 0.035f, intArrayOf(0xFF6AB0, 0x8E6BFF, 0x4A8CFF, 0xFF9A6A), 0.75f, 0.4f)
        }
        piece("abstrakt-lichtkugeln", "Lichtkugeln", ArtGroup.Abstract, "Weiche Kugeln aus Licht, ineinander verlaufend.") {
            sky(0f to 0x080820, 1f to 0x140C30)
            orbs(
                listOf(
                    Orb(0.25f, 0.22f, 0.55f, 0x4A6CFF, 0.65f), Orb(0.85f, 0.40f, 0.5f, 0xFF4A9A, 0.55f),
                    Orb(0.3f, 0.62f, 0.6f, 0x8A4AFF, 0.55f), Orb(0.78f, 0.82f, 0.55f, 0x2AD0C0, 0.5f),
                    Orb(0.5f, 0.45f, 0.35f, 0xFFB04A, 0.35f),
                ),
            )
        }
        piece("abstrakt-ringe", "Ringe", ArtGroup.Abstract, "Ringe aus Licht, nach außen verklingend.") {
            sky(0f to 0x06101E, 1f to 0x0C2038)
            glow(fw * 0.5f, fh * 0.45f, fw * 0.8f, 0x2A8CFF, 0.30f, 2f)
            ringsArt(fw * 0.5f, fh * 0.45f, 26, fw * 0.06f, fw * 0.045f, 0x7AC8FF, 0.9f)
        }
        piece("abstrakt-kristall", "Kristall", ArtGroup.Abstract, "Facetten in Blau und Violett, jede ein wenig anders hell.") {
            crystal(0x1A2A7A, 0x9A4AD8, 7, 15)
        }
        piece("abstrakt-linien", "Fließende Linien", ArtGroup.Abstract, "Linien, die wie Wind durch das Bild laufen.") {
            sky(0f to 0x0A0A1E, 1f to 0x141030)
            flow(46, fh * 0.5f, fh * 0.5f, fh * 0.04f, 0x5AC8FF, 0xFF6AB0, 0.75f)
        }
        piece("abstrakt-verlauf", "Verlauf", ArtGroup.Abstract, "Ein weicher Farbverlauf in Rosa, Orange und Blau.") {
            sky(0f to 0x2A1A6A, 0.5f to 0xC8487A, 1f to 0xFF9A5A)
            orbs(listOf(Orb(0.2f, 0.15f, 0.7f, 0x4A8CFF, 0.6f), Orb(0.9f, 0.7f, 0.6f, 0xFFD070, 0.45f), Orb(0.4f, 0.9f, 0.5f, 0xFF5A9A, 0.4f)))
        }

        // -------------------------------------------------------------------------------------------------------------
        // ZENITH
        // -------------------------------------------------------------------------------------------------------------
        piece("zenith-horizont", "Horizont", ArtGroup.Zenith, "Das Theme von ZENITH 21: die Sonne geht hinter drei Bergrücken auf.") {
            sky(0f to 0x050B24, 0.45f to 0x1A3A78, 0.78f to 0x6A8CC8, 1f to 0xF0B070)
            stars(80, 0f, 0.4f, bright = 0.6f, tint = 0xDDEBFF)
            sun(fw * 0.5f, fh * 0.68f, fw * 0.07f, 0xFFE8B0, 0xFFB070, 0.6f)
            peaks(fh * 0.74f, fh * 0.12f, 4f, 100, 0x3A5A98, 0x24407A, fh * 0.9f, 1f)
            fogBand(fh * 0.72f, fh * 0.82f, 0xF0B070, 0f, 0.3f)
            peaks(fh * 0.84f, fh * 0.12f, 3.5f, 101, 0x1E3A74, 0x10224A, fh * 1.0f, 6f)
            peaks(fh * 0.95f, fh * 0.10f, 3f, 102, 0x0A1634, 0x040A1A, fh * 1.1f, 11f)
        }
        piece("zenith-z", "Z-Licht", ArtGroup.Zenith, "Das Z von ZENITH aus Metall, mit grünem Licht am Rand.") {
            sky(0f to 0x020507, 0.5f to 0x06100E, 1f to 0x0A1A16)
            stars(120, 0f, 1f, bright = 0.5f, tint = 0xDFFFEF)
            zMark(fw * 0.5f, fh * 0.42f, fw * 0.62f)
            text("ZENITH", fw * 0.5f, fh * 0.80f, fw * 0.075f, 0xDFFFEF, 0.85f, 0.35f)
        }
        piece("zenith-comet", "Comet-Glas", ArtGroup.Zenith, "Blasen aus klarem Glas steigen auf – wie bei „Be Comet“.") {
            sky(0f to 0x061438, 0.5f to 0x0E2A70, 1f to 0x1E50B0)
            glow(fw * 0.5f, fh * 0.3f, fw * 0.9f, 0x6AB0FF, 0.25f, 2f)
            bubbles(34, 0.02f, 0.2f)
        }
        piece("zenith-aim", "Aim Higher.", ArtGroup.Zenith, "Der Spruch von ZENITH, unter einem Himmel, der nach oben zieht.") {
            sky(0f to 0xA8C8F8, 0.35f to 0x4A7ADC, 0.8f to 0x10246A, 1f to 0x050C28)
            glow(fw * 0.5f, 0f, fw * 0.9f, 0xFFFFFF, 0.5f, 1.6f)
            peaks(fh * 0.92f, fh * 0.14f, 3f, 110, 0x0C1C50, 0x060E2C, fh * 1.1f, 1.2f)
            text("Aim Higher.", fw * 0.5f, fh * 0.7f, fw * 0.1f, 0xFFFFFF, 0.92f, 0.05f)
        }
    }
}
