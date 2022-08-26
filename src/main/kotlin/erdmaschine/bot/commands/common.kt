package erdmaschine.bot.commands

import erdmaschine.bot.model.Fav
import erdmaschine.bot.model.Storage
import erdmaschine.bot.replyError
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.interactions.InteractionHook

suspend fun JDA.getUser(userId: String): User =
    this.getUserById(userId) ?: this.retrieveUserById(userId).submit().await()

suspend fun getTopAuthors(favs: Collection<Fav>, jda: JDA): Collection<String> =
    favs.groupBy { it.authorId }
        .mapValues { grouping -> grouping.value.distinctBy { it.messageId }.size }
        .entries
        .sortedByDescending { it.value }
        .take(5)
        .map { entry ->
            val name = jda.getUser(entry.key).name
            "$name: ${entry.value}"
        }

fun getTopTags(favs: Collection<Fav>): Collection<String> {
    val tagCount = mutableMapOf<String, Int>()
    favs
        .forEach { fav ->
            fav.tags.forEach {
                val count = tagCount[it] ?: 0
                tagCount[it] = count + 1
            }
        }

    return tagCount.entries
        .sortedByDescending { it.value }
        .take(5)
        .map { entry -> "${entry.key}: ${entry.value}" }
}

suspend fun getTopUsed(favs: Collection<Fav>, jda: JDA): Collection<String> {
    return favs
        .sortedByDescending { it.used }
        .take(5)
        .map {
            val name = jda.getUser(it.authorId).name
            "${it.id} ($name): ${it.used}"
        }
}

suspend fun Collection<Fav>.toVotesList(jda: JDA): Collection<String> = this.map {
    val name = jda.getUser(it.authorId).name
    "${it.id} ($name): ${it.votes.withExplicitSign()}"
}

private fun Int.withExplicitSign(): String = when (this > 0) {
    true -> "+$this"
    else -> "$this"
}

suspend fun retrieveMessageWithErrorHandling(
    fav: Fav,
    storage: Storage,
    interaction: InteractionHook,
    channel: MessageChannel
): Message? {
    try {
        return channel.retrieveMessageById(fav.messageId).submit().await()
    } catch (e: Exception) {
        with(e.message.orEmpty()) {
            if (contains("10008: Unknown Message")) {
                interaction.replyError(
                    "Fav [${fav.id}] points to a removed message.\n"
                            + "It will be removed so this doesn't happen again.",
                    fav.id
                )
                storage.removeFav(fav.id)
            }

            if (contains("Missing permission")) {
                interaction.replyError(
                    "No permission to channel:\n${fav.channelUrl()}\nPlease check my privileges.",
                    fav.id
                )
            }
        }
    }
    return null
}

suspend fun EmbedBuilder.writeStats(favs: Collection<Fav>, jda: JDA) = coroutineScope {
    val usedCount = async { favs.sumOf { it.used } }
    val topAuthors = async { getTopAuthors(favs, jda) }
    val topTags = async { getTopTags(favs) }
    val topUsed = async { getTopUsed(favs, jda) }
    val highestVotes = async { favs.sortedByDescending { it.votes }.take(5).toVotesList(jda) }
    val lowestVotes = async { favs.sortedBy { it.votes }.take(5).toVotesList(jda) }

    with(this) {
        addField("Counts", "Saved: ${favs.count()}\nPosted: ${usedCount.await()}", true)
        addField("Top authors", topAuthors.await().joinToString("\n"), true)
        addField("Top tags", topTags.await().joinToString("\n"), true)
        addField("Top posted", topUsed.await().joinToString("\n"), true)
        addField("Highest votes", highestVotes.await().joinToString("\n"), true)
        addField("Lowest votes", lowestVotes.await().joinToString("\n"), true)
    }
}
