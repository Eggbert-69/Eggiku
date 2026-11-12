package mihon.domain.migration.usecases

import eu.kanade.tachiyomi.data.duplicates.EggikuDuplicates
import tachiyomi.domain.chapter.model.Chapter
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

// KMK -->
/**
 * Eggiku migration helpers: lining up chapter numbers between sources, telling how sure a match
 * is, and scoring which source is the best place to move to.
 */
object EggikuMigrationSmarts {

    /** How many migrations in the current batch had their chapter numbering lined up */
    val offsetsApplied = AtomicInteger(0)

    private val chapterWord = Regex("""^\s*(vol(ume)?\.?\s*\d+\s*)?(chapter|chap|ch|episode|ep)?\.?\s*[\d.]+\s*[-:–.]?\s*""", RegexOption.IGNORE_CASE)

    /** The text title of a chapter ("Ch. 5 - The Storm" -> "the storm"), or null if it has none */
    private fun titleOf(chapter: Chapter): String? = chapter.name
        .replace(chapterWord, "")
        .lowercase()
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
        .trim()
        .takeIf { it.length >= 3 && it.any(Char::isLetter) }

    /**
     * How much to add to an old chapter number to get the same chapter on the new source.
     * Returns 0 unless there's good evidence: chapter titles that match up only with a shift, or
     * the classic case of one source starting at chapter 0 and the other at chapter 1 with the
     * same chapter count. Being wrong here would mark the wrong chapters read, so it stays strict.
     */
    fun chapterOffset(old: List<Chapter>, new: List<Chapter>): Double {
        val oldRecognized = old.filter { it.isRecognizedNumber }
        val newRecognized = new.filter { it.isRecognizedNumber }
        if (oldRecognized.size < 3 || newRecognized.size < 3) return 0.0

        // 1) Titles: if chapters have real names, see which shift makes the most of them line up
        val oldTitles = oldRecognized.mapNotNull { c -> titleOf(c)?.let { c.chapterNumber to it } }.toMap()
        val newTitles = newRecognized.mapNotNull { c -> titleOf(c)?.let { c.chapterNumber to it } }.toMap()
        if (oldTitles.size >= 3 && newTitles.size >= 3) {
            fun matches(shift: Double) = oldTitles.count { (number, title) -> newTitles[number + shift] == title }
            val none = matches(0.0)
            val best = CANDIDATE_SHIFTS.maxByOrNull { matches(it) } ?: return 0.0
            val bestCount = matches(best)
            return if (bestCount >= 3 && bestCount > none * 2) best else 0.0
        }

        // 2) Starts at 0 vs starts at 1, with the same number of chapters
        val oldNumbers = oldRecognized.map { it.chapterNumber }.distinct()
        val newNumbers = newRecognized.map { it.chapterNumber }.distinct()
        if (abs(oldNumbers.size - newNumbers.size) > 0) return 0.0
        val oldMin = oldNumbers.min()
        val newMin = newNumbers.min()
        val shift = newMin - oldMin
        val zeroStartSwap = (oldMin == 0.0 && newMin == 1.0) || (oldMin == 1.0 && newMin == 0.0)
        val sameSpread = (newNumbers.max() - oldNumbers.max()) == shift
        return if (zeroStartSwap && sameSpread) shift else 0.0
    }

    /** Read chapter numbers on the old entry that exist on the new one, after lining up numbering */
    fun carriedReadCount(old: List<Chapter>, new: List<Chapter>): Pair<Int, Int> {
        val offset = chapterOffset(old, new)
        val readNumbers = old.filter { it.read && it.isRecognizedNumber }.map { it.chapterNumber }.toSet()
        val newNumbers = new.map { it.chapterNumber }.toSet()
        return readNumbers.count { (it + offset) in newNumbers } to readNumbers.size
    }

    /** 0..1, how alike two titles are (letter pairs in common, ignoring case and punctuation) */
    fun titleSimilarity(a: String, b: String): Double {
        val x = EggikuDuplicates.normalize(a)
        val y = EggikuDuplicates.normalize(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        if (x == y || x.contains(y) || y.contains(x)) return 1.0
        val pairsX = x.windowed(2).groupingBy { it }.eachCount()
        val pairsY = y.windowed(2).groupingBy { it }.eachCount()
        val shared = pairsX.entries.sumOf { (pair, count) -> minOf(count, pairsY[pair] ?: 0) }
        val total = (x.length - 1) + (y.length - 1)
        return if (total <= 0) 0.0 else 2.0 * shared / total
    }

    /**
     * A match is "unsure" when the titles don't look alike, or the new entry has far fewer chapters
     * than the one being replaced. Those wait for a tap to confirm before they move.
     */
    fun isUnsure(currentTitle: String, currentChapters: Int, targetTitle: String, targetChapters: Int): Boolean {
        if (titleSimilarity(currentTitle, targetTitle) < UNSURE_TITLE_SIMILARITY) return true
        return currentChapters >= 10 && targetChapters < currentChapters / 2
    }

    private val CANDIDATE_SHIFTS = listOf(-2.0, -1.0, -0.5, 0.5, 1.0, 2.0)
    private const val UNSURE_TITLE_SIMILARITY = 0.5
}
// KMK <--
