package dev.hearth.launcher.wallpaper

import android.graphics.Bitmap

/*
 * ZENITH 21.5 – the gallery's drawn pictures: space, landscapes, abstract art and ZENITH's own, all drawn by ZENITH
 * itself (no picture files), in the size of the phone's screen. Each piece is a short recipe for the brush in ArtCanvas.
 * (The Moon and the planets are not among them: next to real photos the drawn ones looked cheap, so the gallery shows
 * the real photos of NASA for them, see NasaLibrary.)
 */

internal enum class ArtGroup(val label: String) {
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

    val all: List<ArtPiece> = buildList {
        fun piece(id: String, title: String, group: ArtGroup, note: String, draw: ArtCanvas.() -> Unit) {
            add(ArtPiece(id, title, group, note, draw))
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
