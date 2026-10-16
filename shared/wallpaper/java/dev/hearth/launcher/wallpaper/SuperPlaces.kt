package dev.hearth.launcher.wallpaper

import kotlin.math.PI

/** A place on a body: [lon] (east is positive) and [lat] (north is positive) in degrees, read off the map the body is turned from. */
class SuperPlace(val name: String, val lon: Float, val lat: Float) {
    /** The turn about the polar axis that brings the place to the middle (radians). */
    val spin: Float get() = -lon * (PI.toFloat() / 180f)

    /** The latitude of the middle of the picture when the place is in it (radians). */
    val tilt: Float get() = lat * (PI.toFloat() / 180f)
}

/*
 * Where the body lands when it has finished turning: the places of every body that can be turned (the Moon and the planets;
 * the photos and the geometry have no surface to choose from). The numbers come from the maps
 * the bodies are turned from (every map is checked against its own picture, with the places' names known from the
 * missions: Tycho, Olympus Mons, Pele …). The list's last number – [count] – is the "surprise": a different place every time.
 */
object SuperPlaces {

    private val moon = listOf(
        SuperPlace("Tycho", -11.4f, -43.4f),
        SuperPlace("Kopernikus", -20.1f, 9.6f),
        SuperPlace("Apollo 11", 23.5f, 0.7f),
        SuperPlace("Apollo 17", 30.8f, 20.2f),
        SuperPlace("Mare Imbrium", -15.6f, 32.8f),
        SuperPlace("Mare Crisium", 59.1f, 17.0f),
        SuperPlace("Aristarchus", -47.4f, 23.7f),
        SuperPlace("Plato", -9.3f, 51.6f),
        SuperPlace("Mare Orientale", -92.8f, -19.4f),
        SuperPlace("Tsiolkovsky", 129.1f, -20.4f),
        SuperPlace("Mare Moscoviense", 147.9f, 27.3f),
        SuperPlace("Apollo 15", 3.6f, 26.1f),
        SuperPlace("Clavius", -14.4f, -58.8f),
    )

    private val mars = listOf(
        SuperPlace("Olympus Mons", -133.8f, 18.7f),
        SuperPlace("Tharsis-Vulkane", -113f, 1f),
        SuperPlace("Valles Marineris", -70f, -12f),
        SuperPlace("Syrtis Major", 69f, 9f),
        SuperPlace("Hellas", 66f, -42f),
        SuperPlace("Nordpolkappe", 0f, 80f),
        SuperPlace("Südpolkappe", 0f, -80f),
    )

    private val venus = listOf(
        SuperPlace("Maxwell Montes", 5f, 65f),
        SuperPlace("Aphrodite Terra", 115f, -8f),
        SuperPlace("Beta Regio", -78f, 27f),
        SuperPlace("Alpha Regio", 3f, -23f),
        SuperPlace("Atla Regio", -158f, 6f),
    )

    // The gas planets: read off the maps (2048 × 1024) the way they are turned: Jupiter's from Cassini, Neptune's with the Great Dark Spot of Voyager.
    private val jupiter = listOf(
        SuperPlace("Großer Roter Fleck", -48f, -20f),
        SuperPlace("Weiße Ovale", -76f, -41f),
        SuperPlace("Dunkle Löcher", 135f, 8f),
        SuperPlace("Äquator", -110f, -3f),
        SuperPlace("Nördliche Ovale", 80f, 40f),
        SuperPlace("Südliche Bänder", 150f, -40f),
    )

    // Saturn's rings stay as they are, so the bands are only turned a little to the north or the south.
    private val saturn = listOf(
        SuperPlace("Äquatorzone", 0f, 2f),
        SuperPlace("Heller Gürtel", -90f, 12f),
        SuperPlace("Nördliche Bänder", 130f, 40f),
        SuperPlace("Südliche Bänder", 100f, -40f),
        SuperPlace("Nordpolregion", 20f, 78f),
    )

    private val neptune = listOf(
        SuperPlace("Großer Dunkler Fleck", 18f, -16f),
        SuperPlace("Scooter", 6f, -23f),
        SuperPlace("Zweiter Dunkler Fleck", 74f, -54f),
        SuperPlace("Helle Wolken Süd", 144f, -41f),
        SuperPlace("Nördliche Wolken", -97f, 28f),
        SuperPlace("Heller Äquator", -60f, 0f),
    )

    private val pluto = listOf(
        SuperPlace("Sputnik Planitia", 178f, 20f),
        SuperPlace("Cthulhu Macula", 130f, -8f),
        SuperPlace("Norgay Montes", 162f, 14f),
        SuperPlace("Doppelkrater", 100f, 6f),
    )

    private val io = listOf(
        SuperPlace("Loki Patera", 52f, 14f),
        SuperPlace("Pele", 104f, -19f),
        SuperPlace("Prometheus", -157f, -1f),
        SuperPlace("Heller Norden", 135f, 40f),
    )

    private val europa = listOf(
        SuperPlace("Pwyll", 89f, -25f),
        SuperPlace("Conamara Chaos", 86.5f, 9.4f),
        SuperPlace("Linien-Netz", 150f, 35f),
        SuperPlace("Helle Ebene", -100f, 10f),
    )

    private val ganymede = listOf(
        SuperPlace("Tros", -27f, 13f),
        SuperPlace("Galileo Regio", -135f, 25f),
        SuperPlace("Osiris", -166f, -38f),
        SuperPlace("Dunkle Felder", -60f, 35f),
    )

    private val earth = listOf(
        SuperPlace("Alpen und Mittelmeer", 10f, 44f),
        SuperPlace("Himalaya", 86f, 29f),
        SuperPlace("Sahara und Nil", 20f, 24f),
        SuperPlace("Amazonas", -62f, -5f),
        SuperPlace("Australien", 134f, -25f),
        SuperPlace("Grönland", -42f, 70f),
        SuperPlace("Nordamerika", -100f, 40f),
        SuperPlace("Japan", 138f, 36f),
    )

    /** The places of [scene]; none if it has no surface to choose from. */
    fun of(scene: SuperScene): List<SuperPlace> = when (scene) {
        SuperScene.Moon -> moon
        SuperScene.Earth -> earth
        SuperScene.Mars -> mars
        SuperScene.Venus -> venus
        SuperScene.Jupiter -> jupiter
        SuperScene.Saturn -> saturn
        SuperScene.Neptune -> neptune
        SuperScene.Pluto -> pluto
        SuperScene.Io -> io
        SuperScene.Europa -> europa
        SuperScene.Ganymede -> ganymede
        else -> emptyList()
    }

    /** How many places [scene] has; the number itself stands for the surprise. */
    fun count(scene: SuperScene): Int = of(scene).size

    /** The name of choice [index] of [scene] ("Überraschung" for the last). */
    fun label(scene: SuperScene, index: Int): String {
        val list = of(scene)
        return list.getOrNull(index)?.name ?: "Überraschung"
    }

    /** The place that choice [index] means; [random] (0 … 1) picks one for the surprise. Null if [scene] has none. */
    fun resolve(scene: SuperScene, index: Int, random: Float): SuperPlace? {
        val list = of(scene)
        if (list.isEmpty()) return null
        return list.getOrNull(index) ?: list[(random * list.size).toInt().coerceIn(0, list.size - 1)]
    }

    /** The latitude of the middle of the picture before the body has landed (radians): a little off the equator. */
    fun restTilt(scene: SuperScene): Float = if (scene == SuperScene.Moon) -0.117f else -0.2f
}
