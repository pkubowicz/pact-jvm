package au.com.dius.pact.model.matchingrules

import au.com.dius.pact.model.PactSpecVersion
import mu.KLogging
import java.lang.IllegalArgumentException

/**
 * Logic to use to combine rules
 */
enum class RuleLogic {
  AND, OR
}

/**
 * Matching rule
 */
interface MatchingRule {
  fun toMap(): Map<String, Any?>
}

/**
 * Matching Rule for dates
 */
data class DateMatcher @JvmOverloads constructor(val format: String = "yyyy-MM-dd") : MatchingRule {
  override fun toMap() = mapOf("match" to "date", "date" to format)
}

/**
 * Matching rule for equality
 */
object EqualsMatcher : MatchingRule {
  override fun toMap() = mapOf("match" to "equality")
}

/**
 * Matcher for a substring in a string
 */
data class IncludeMatcher(val value: String) : MatchingRule {
  override fun toMap() = mapOf("match" to "include", "value" to value)
}

/**
 * Type matching with a maximum size
 */
data class MaxTypeMatcher(val max: Int) : MatchingRule {
  override fun toMap() = mapOf("match" to "type", "max" to max)
}

/**
 * Type matcher with a minimum size and maximum size
 */
data class MinMaxTypeMatcher(val min: Int, val max: Int) : MatchingRule {
  override fun toMap() = mapOf("match" to "type", "min" to min, "max" to max)
}

/**
 * Type matcher with a minimum size
 */
data class MinTypeMatcher(val min: Int) : MatchingRule {
  override fun toMap() = mapOf("match" to "type", "min" to min)
}

/**
 * Type matching for numbers
 */
data class NumberTypeMatcher(val numberType: NumberType) : MatchingRule {
  enum class NumberType {
    NUMBER,
    INTEGER,
    DECIMAL
  }

  override fun toMap() = mapOf("match" to numberType.name.toLowerCase())
}

/**
 * Regular Expression Matcher
 */
data class RegexMatcher @JvmOverloads constructor (val regex: String, val example: String? = null) : MatchingRule {
  override fun toMap() = mapOf("match" to "regex", "regex" to regex)
}

/**
 * Matcher for time values
 */
data class TimeMatcher @JvmOverloads constructor(val format: String = "HH:mm:ss") : MatchingRule {
  override fun toMap() = mapOf("match" to "time", "time" to format)
}

/**
 * Matcher for time values
 */
data class TimestampMatcher @JvmOverloads constructor(val format: String = "yyyy-MM-dd HH:mm:ssZZZ") : MatchingRule {
  override fun toMap() = mapOf("match" to "timestamp", "timestamp" to format)
}

/**
 * Matcher for types
 */
object TypeMatcher : MatchingRule {
  override fun toMap() = mapOf("match" to "type")
}

/**
 * Matcher for null values
 */
object NullMatcher : MatchingRule {
  override fun toMap() = mapOf("match" to "null")
}

/**
 * Matcher for values in a map, ignoring the keys
 */
object ValuesMatcher : MatchingRule {
  override fun toMap() = mapOf("match" to "values")
}

data class MatchingRuleGroup @JvmOverloads constructor(
  val rules: MutableList<MatchingRule> = mutableListOf(),
  val ruleLogic: RuleLogic = RuleLogic.AND
) {
  fun toMap(pactSpecVersion: PactSpecVersion): Map<String, Any?> {
    return if (pactSpecVersion < PactSpecVersion.V3) {
      rules.first().toMap()
    } else {
      mapOf("matchers" to rules.map { it.toMap() }, "combine" to ruleLogic.name)
    }
  }

  companion object : KLogging() {
    fun fromMap(map: Map<String, Any?>): MatchingRuleGroup {
      var ruleLogic = RuleLogic.AND
      if (map.containsKey("combine")) {
        try {
          ruleLogic = RuleLogic.valueOf(map["combine"] as String)
        } catch (e: IllegalArgumentException) {
          logger.warn { "${map["combine"]} is not a valid matcher rule logic value" }
        }
      }

      val rules = mutableListOf<MatchingRule>()
      if (map.containsKey("matchers")) {
        val matchers = map["matchers"]
        if (matchers is List<*>) {
          matchers.forEach {
            if (it is Map<*, *>) {
              rules.add(ruleFromMap(it as Map<String, Any?>))
            }
          }
        } else {
          logger.warn { "Map $map does not contain a list of matchers" }
        }
      }

      return MatchingRuleGroup(rules, ruleLogic)
    }

    private const val MATCH = "match"
    private const val MIN = "min"
    private const val MAX = "max"
    private const val REGEX = "regex"
    private const val TIMESTAMP = "timestamp"
    private const val TIME = "time"
    private const val DATE = "date"

    private fun mapEntryToInt(map: Map<String, Any?>, field: String) =
      if (map[field] is Int) map[field] as Int
      else Integer.parseInt(map[field]!!.toString())

    @JvmStatic
    fun ruleFromMap(map: Map<String, Any?>): MatchingRule {
      return when {
        map.containsKey(MATCH) -> when (map[MATCH]) {
          REGEX -> RegexMatcher(map[REGEX] as String)
          "equality" -> EqualsMatcher
          "null" -> NullMatcher
          "include" -> IncludeMatcher(map["value"].toString())
          "type" -> {
            if (map.containsKey(MIN) && map.containsKey(MAX)) {
              MinMaxTypeMatcher(mapEntryToInt(map, MIN), mapEntryToInt(map, MAX))
            } else if (map.containsKey(MIN)) {
              MinTypeMatcher(mapEntryToInt(map, MIN))
            } else if (map.containsKey(MAX)) {
              MaxTypeMatcher(mapEntryToInt(map, MAX))
            } else {
              TypeMatcher
            }
          }
          "number" -> NumberTypeMatcher(NumberTypeMatcher.NumberType.NUMBER)
          "integer" -> NumberTypeMatcher(NumberTypeMatcher.NumberType.INTEGER)
          "decimal" -> NumberTypeMatcher(NumberTypeMatcher.NumberType.DECIMAL)
          "real" -> {
            logger.warn { "The 'real' type matcher is deprecated, use 'decimal' instead" }
            NumberTypeMatcher(NumberTypeMatcher.NumberType.DECIMAL)
          }
          MIN -> MinTypeMatcher(mapEntryToInt(map, MIN))
          MAX -> MaxTypeMatcher(mapEntryToInt(map, MAX))
          TIMESTAMP ->
            if (map.containsKey(TIMESTAMP)) TimestampMatcher(map[TIMESTAMP].toString())
            else TimestampMatcher()
          TIME ->
            if (map.containsKey(TIME)) TimeMatcher(map[TIME].toString())
            else TimeMatcher()
          DATE ->
            if (map.containsKey(DATE)) DateMatcher(map[DATE].toString())
            else DateMatcher()
          "values" -> ValuesMatcher
          else -> {
            logger.warn { "Unrecognised matcher ${map[MATCH]}, defaulting to equality matching" }
            EqualsMatcher
          }
        }
        map.containsKey(REGEX) -> RegexMatcher(map[REGEX] as String)
        map.containsKey(MIN) -> MinTypeMatcher(mapEntryToInt(map, MIN))
        map.containsKey(MAX) -> MaxTypeMatcher(mapEntryToInt(map, MAX))
        map.containsKey(TIMESTAMP) -> TimestampMatcher(map[TIMESTAMP] as String)
        map.containsKey(TIME) -> TimeMatcher(map[TIME] as String)
        map.containsKey(DATE) -> DateMatcher(map[DATE] as String)
        else -> {
          logger.warn { "Unrecognised matcher definition $map, defaulting to equality matching" }
          EqualsMatcher
        }
      }
    }
  }
}

/**
 * Collection of all matching rules
 */
interface MatchingRules {
  /**
   * Get all the rules for a given category
   */
  fun rulesForCategory(category: String): Category

  /**
   * Adds a new category with the given name to the collection
   */
  fun addCategory(category: String): Category

  /**
   * Adds the category to the collection
   */
  fun addCategory(category: Category): Category
}
