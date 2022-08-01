package erdmaschine.bot.commands

import erdmaschine.bot.await
import erdmaschine.bot.forMessage
import erdmaschine.bot.model.Fav
import erdmaschine.bot.model.Storage
import erdmaschine.bot.replyError
import erdmaschine.bot.weightedRandom
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands

private const val OPTION_TAG = "tag"
private const val OPTION_ID = "id"

val FavCommand =
    Commands.slash("fav", "Post a random fav, filtered by the given tags or from all favs")
        .addOption(
            OptionType.STRING,
            OPTION_TAG,
            "Limit the favs to only include favs with at least one of these (space-separated) tags"
        )
        .addOption(
            OptionType.STRING,
            OPTION_ID,
            "Post the fav associated with this specific ID"
        )

suspend fun executeFavCommand(storage: Storage, event: SlashCommandInteractionEvent) {
    val id = event.getOption(OPTION_ID)?.asString.orEmpty()
    val tags = event.getOption(OPTION_TAG)?.asString?.split(" ").orEmpty()
    val guildIds = event.jda.guilds.map { it.id }

    val interaction = event.reply("Fetching candidates...").await()

    val candidates = mutableListOf<Fav>()
    if (id.isNotBlank()) {
        val fav = storage.getFav(id)
            ?: return interaction.replyError("Fav with id [$id] not found")
        candidates.add(fav)
    } else {
        storage
            .getFavs(event.user.id, event.guild?.id, tags)
            .filter { guildIds.contains(it.guildId) }
            .also { candidates.addAll(it) }
    }

    val fav = candidates.weightedRandom()
        ?: return interaction.replyError("No favs found")

    storage.increaseUsed(fav)

    val guild = event.jda.guilds
        .firstOrNull { it.id == fav.guildId }
        ?: return interaction.replyError("Guild not found:\n${fav.guildUrl()}", fav.id)
    val channel = guild.getTextChannelById(fav.channelId)
        ?: guild.getThreadChannelById(fav.channelId)
        ?: return interaction.replyError("Channel not found:\n${fav.channelUrl()}", fav.id)

    val message = retrieveMessageWithErrorHandling(fav, storage, interaction, channel) ?: return
    val embed = EmbedBuilder().forMessage(message, fav.id).build()

    interaction.editOriginal(getFavMessage()).await()
    interaction.editOriginalEmbeds(embed).await()

    val original = interaction.retrieveOriginal().await()
    original.addReaction("👍").await()
    original.addReaction("👎").await()
}
