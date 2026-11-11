package com.nylio.ssokssok.ui.home

import com.nylio.ssokssok.data.db.DeckPath
import com.nylio.ssokssok.data.db.DeckSummary

/**
 * A folder or deck in the home tree. Folders come from deck names ("서울대 한국어+::3A::1-1 …").
 * A deck whose name is also a folder of other decks keeps both roles.
 */
class DeckNode(
    val path: String,
    val name: String,
    val depth: Int,
    val deck: DeckSummary?,
    val children: List<DeckNode>,
) {
    val isFolder get() = children.isNotEmpty()

    /** Every deck at or under this node. */
    val decks: List<DeckSummary> by lazy { listOfNotNull(deck) + children.flatMap { it.decks } }
    val deckIds: List<Long> get() = decks.map { it.deck.id }
    val total get() = decks.sumOf { it.total }
    val due get() = decks.sumOf { it.due }
    val learned get() = decks.sumOf { it.learned }
    fun newToday(perDeck: (DeckSummary) -> Int) = decks.sumOf(perDeck)
}

object DeckTree {
    fun build(decks: List<DeckSummary>): List<DeckNode> = level(decks.map { it to DeckPath(it.deck.name).parts }, emptyList())

    private fun level(items: List<Pair<DeckSummary, List<String>>>, prefix: List<String>): List<DeckNode> {
        val depth = prefix.size
        return items.groupBy { it.second[depth] }
            .map { (name, group) ->
                val here = prefix + name
                val own = group.firstOrNull { it.second.size == here.size }?.first
                val deeper = group.filter { it.second.size > here.size }
                DeckNode(here.joinToString(DeckPath.SEP), name, depth, own, level(deeper, here))
            }
            .sortedWith { a, b -> naturalCompare(a.name, b.name) }
    }

    /** Every folder path in the tree, outermost first. */
    fun folders(nodes: List<DeckNode>): List<String> = nodes.filter { it.isFolder }.flatMap { listOf(it.path) + folders(it.children) }

    /** Rows to show: folders, and their contents when expanded. */
    fun visible(nodes: List<DeckNode>, expanded: Set<String>): List<DeckNode> = buildList {
        fun walk(list: List<DeckNode>) {
            for (n in list) {
                add(n)
                if (n.isFolder && n.path in expanded) {
                    // A deck that is also a folder shows itself first inside the folder.
                    n.deck?.let { add(DeckNode(n.path + "#self", n.name, n.depth + 1, it, emptyList())) }
                    walk(n.children)
                }
            }
        }
        walk(nodes)
    }

    /** "1-2" before "1-10", "2A" before "10A". */
    fun naturalCompare(a: String, b: String): Int {
        val ta = CHUNK.findAll(a).map { it.value }.toList()
        val tb = CHUNK.findAll(b).map { it.value }.toList()
        for (i in 0 until minOf(ta.size, tb.size)) {
            val x = ta[i]
            val y = tb[i]
            val c = if (x[0].isDigit() && y[0].isDigit()) {
                compareValuesBy(x, y, { it.trimStart('0').length }, { it.trimStart('0') })
            } else {
                x.compareTo(y, ignoreCase = true)
            }
            if (c != 0) return c
        }
        return ta.size - tb.size
    }

    private val CHUNK = Regex("\\d+|\\D+")
}
