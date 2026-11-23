package app.pocketdex.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class TypeInfo(val id: Int, val name: L)

data class Ability(val id: Int, val name: L, val desc: L)

data class Move(
    val id: Int,
    val name: L,
    val type: Int,
    val power: Int,
    val accuracy: Int,
    val pp: Int,
    /** 1 = status, 2 = physical, 3 = special */
    val damageClass: Int,
)

data class Species(
    val id: Int,
    val name: L,
    val genus: L,
    val heightDm: Int,
    val weightHg: Int,
    val types: List<Int>,
    /** HP, Attack, Defense, Sp. Atk, Sp. Def, Speed */
    val stats: List<Int>,
    /** ability id to isHidden */
    val abilities: List<Pair<Int, Boolean>>,
    val flavor: L,
    val generation: L,
    val captureRate: Int,
    /** -1 = genderless, otherwise female ratio in eighths */
    val genderRate: Int,
    val eggGroups: List<L>,
    val color: L,
    val legendary: Boolean,
    val mythical: Boolean,
    val baby: Boolean,
    val evolvesFrom: Int,
    val chainId: Int,
    val evoCondition: L,
    /** move id to level learned (0 = on evolution) */
    val moves: List<Pair<Int, Int>>,
) {
    val total: Int get() = stats.sum()
    val number: String get() = id.toString().padStart(4, '0')
    val heightM: String get() = "%.1f m".format(heightDm / 10f)
    val weightKg: String get() = "%.1f kg".format(weightHg / 10f)
}

class Dex(
    val types: List<TypeInfo>,
    private val effectiveness: Array<FloatArray>,
    val abilities: Map<Int, Ability>,
    val moves: Map<Int, Move>,
    val species: List<Species>,
) {
    val maxId: Int get() = species.size

    operator fun get(id: Int): Species? = species.getOrNull(id - 1)

    fun typeName(id: Int, lang: Lang): String = types.getOrNull(id - 1)?.name?.get(lang) ?: "?"

    private val byChain: Map<Int, List<Species>> = species.groupBy { it.chainId }
    private val byEnglish: Map<String, Species> = species.associateBy { normalize(it.name.en) }

    fun chain(s: Species): List<Species> = byChain[s.chainId].orEmpty()

    /** Depth of a species inside its evolution chain (0 = base form). */
    fun stage(s: Species): Int {
        var d = 0
        var cur = s
        while (cur.evolvesFrom != 0) {
            cur = this[cur.evolvesFrom] ?: break
            d++
        }
        return d
    }

    fun findByEnglishName(name: String): Species? = byEnglish[normalize(name)]

    /** Damage multiplier of each attacking type against a defender with [defTypes]. */
    fun defenseMultipliers(defTypes: List<Int>): Map<Int, Float> =
        (1..18).associateWith { atk ->
            defTypes.fold(1f) { acc, def -> acc * effectiveness[atk - 1][def - 1] }
        }

    companion object {
        private fun normalize(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

        fun load(context: Context): Dex {
            val text = context.assets.open("dex.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(text)

            val types = root.getJSONArray("types").objects().map {
                TypeInfo(it.getInt("id"), L(it.getString("zh"), it.getString("en")))
            }

            val effJson = root.getJSONArray("eff")
            val eff = Array(18) { a ->
                val row = effJson.getJSONArray(a)
                FloatArray(18) { d -> row.getDouble(d).toFloat() }
            }

            // [zh name, zh desc, en name, en desc]
            val abJson = root.getJSONObject("abilities")
            val abilities = abJson.keys().asSequence().associate { k ->
                val a = abJson.getJSONArray(k)
                k.toInt() to Ability(k.toInt(), L(a.getString(0), a.getString(2)), L(a.getString(1), a.getString(3)))
            }

            // [zh name, type, power, accuracy, pp, class, en name]
            val mvJson = root.getJSONObject("moves")
            val moves = mvJson.keys().asSequence().associate { k ->
                val m = mvJson.getJSONArray(k)
                k.toInt() to Move(k.toInt(), L(m.getString(0), m.getString(6)), m.getInt(1), m.getInt(2), m.getInt(3), m.getInt(4), m.getInt(5))
            }

            val species = root.getJSONArray("species").objects().map { o ->
                val eggZh = o.getJSONArray("egg").strings()
                val eggEn = o.getJSONArray("egg_en").strings()
                Species(
                    id = o.getInt("id"),
                    name = L(o.getString("zh"), o.getString("en")),
                    genus = L(o.getString("genus"), o.getString("genus_en")),
                    heightDm = o.getInt("h"),
                    weightHg = o.getInt("w"),
                    types = o.getJSONArray("t").ints(),
                    stats = o.getJSONArray("s").ints(),
                    abilities = o.getJSONArray("ab").arrays().map { it.getInt(0) to (it.getInt(1) == 1) },
                    flavor = L(o.getString("fl"), o.getString("fl_en")),
                    generation = L(o.getString("gen"), o.getString("gen_en")),
                    captureRate = o.getInt("cap"),
                    genderRate = o.getInt("gr"),
                    eggGroups = eggZh.mapIndexed { i, z -> L(z, eggEn.getOrElse(i) { z }) },
                    color = L(o.getString("col"), o.getString("col_en")),
                    legendary = o.getBoolean("leg"),
                    mythical = o.getBoolean("myth"),
                    baby = o.getBoolean("baby"),
                    evolvesFrom = o.getInt("from"),
                    chainId = o.getInt("chain"),
                    evoCondition = L(o.getString("evo"), o.getString("evo_en")),
                    moves = o.getJSONArray("mv").arrays().map { it.getInt(0) to it.getInt(1) },
                )
            }
            return Dex(types, eff, abilities, moves, species)
        }

        private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
        private fun JSONArray.arrays() = (0 until length()).map { getJSONArray(it) }
        private fun JSONArray.ints() = (0 until length()).map { getInt(it) }
        private fun JSONArray.strings() = (0 until length()).map { getString(it) }
    }
}
