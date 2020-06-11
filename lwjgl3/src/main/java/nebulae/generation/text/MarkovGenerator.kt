package nebulae.generation.text;

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import de.mfietz.jhyphenator.HyphenationPattern
import de.mfietz.jhyphenator.Hyphenator
import kotlin.random.Random

//partially inspired by https://github.com/martindevans/CasualGodComplex/blob/42602d135d9e5942d29cace34b47b95724788282/CasualGodComplex/StarName.cs

class MarkovGenerator(private val rand: Random) {

    private val dict = mutableMapOf<String, MutableList<String>>()
    private val startingStrings = mutableListOf<String>()
    private val greekLetters = listOf("Alpha", "Beta", "Gamma", "Delta", "Epsilon", "Zeta", "Eta", "Theta", "Iota", "Kappa", "Lambda", "Mu", "Nu", "Xi", "Omnicron", "Pi", "Rho",
            "Sigma", "Tau", "Upsilon", "Phi", "Chi", "Psi", "Omega")
    private val romanLiterals = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XII", "XIII", "XIV", "XV")
    private val decorators = listOf("Major", "Majoris", "Minor", "Minoris", "Prime", "Secundus", "Tertium", "System")
    private val usedNames = hashSetOf<String>()

    init {
        Gdx.files.internal("data/starnames.txt").readString().trimEnd()
                .split('\n')
                .onEach { baseName ->
                    val res = extractSyllables(baseName.trim().toLowerCase().split(' ').reduce(String::plus))
                            .filter { it.length > 1 }
                    for ((i, s) in res.withIndex()) {
                        val list = dict.computeIfAbsent(s) { mutableListOf() }
                        if (i < res.size - 1) {
                            list.add(res[i + 1])
                        }
                        if (i == 0) {
                            startingStrings.add(s)
                        }
                    }
                }
    }

    private fun extractSyllables(word: String): List<String> {
        val lookups = listOf(HyphenationPattern.FR, HyphenationPattern.EN_US, HyphenationPattern.DE, HyphenationPattern.ES)
        var res = listOf(word)
        for (pattern in lookups) {
            val h = Hyphenator.getInstance(pattern)
            val syls = h.hyphenate(word)
            if (syls.size > 1)
                res = syls
        }
        return res
    }


    private val prefixStrategies = listOf(
            0.1f to { greekLetters.random(rand) + " " },
            0.1f to { Character.toString(rand.nextInt(65, 90)) + "-" }
    )

    private val suffixStrategies = listOf(
            0.1f to { " " + greekLetters.random(rand) },
            0.1f to { " " + romanLiterals.random(rand) },
            0.1f to { " " + decorators.random(rand) }
    )

    fun generate(minSize: Int): String {
        val prefix = prefixStrategies.weighted(rand)
        val name = plainMarkov(minSize)
        val suffix = suffixStrategies.weighted(rand)
        val result = """$prefix$name$suffix"""
        if (usedNames.contains(result)) {
            return generate(minSize)
        }
        usedNames.add(name)
        return result
    }

    fun plainMarkov(minSize: Int): String {
        var curSyl = startingStrings.random(rand)
        var name = curSyl

        while (name.length < minSize) {
            if (dict.keys.contains(curSyl) && dict[curSyl]!!.isNotEmpty()) {
                curSyl = dict[curSyl]!!.random(rand)
                name += curSyl
            } else {
                curSyl = startingStrings.random(rand)
            }
        }
        if (!validate(name)) {
            return generate(minSize)
        }
        return name.capitalize()
    }


    private fun validate(name: String): Boolean {
        return (name.length > 2 && name[0] != name[1]) &&
                name.split("a", "e", "i", "o", "u", "y", "w", ignoreCase = true).all { it.length <= 3 }
    }

}

private fun List<Pair<Float, () -> String>>.weighted(rand: Random): String {
    forEach {
        if (rand.nextFloat() < it.first)
            return it.second.invoke()
    }
    return ""
}

fun main() {
    val list = object : ApplicationAdapter() {
        override fun create() {
            val r = Random(123)
            val mark = MarkovGenerator(r)
            for (s in 0 until 100)
                println(mark.generate(r.nextInt(4, 8)))
        }
    }
    Lwjgl3Application(list, defaultConfiguration)
}


private val defaultConfiguration: Lwjgl3ApplicationConfiguration
    get() {
        return Lwjgl3ApplicationConfiguration().apply {
            setTitle("Nebulae")
            setWindowedMode(Lwjgl3ApplicationConfiguration.getDisplayMode().width, Lwjgl3ApplicationConfiguration.getDisplayMode().height)
            setMaximized(true)
            setWindowIcon("libgdx128.png", "libgdx64.png", "libgdx32.png", "libgdx16.png")
//                useOpenGL3(true, 4, 3)
        }
    }