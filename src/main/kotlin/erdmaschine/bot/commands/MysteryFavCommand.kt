package erdmaschine.bot.commands

import erdmaschine.bot.await
import erdmaschine.bot.forMessage
import erdmaschine.bot.model.Storage
import erdmaschine.bot.replyError
import erdmaschine.bot.weightedRandom
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands

private const val SELF = "self"
private const val OPTION_UWU = "uwu"
private const val OPTION_SPONGE = "sponge"

val MysteryFavCommand =
    Commands.slash("mystery", "Post a random fav (even of other users), without revealing the author")
        .addOption(OptionType.BOOLEAN, SELF, "Only choose mystery fav from your own favs")
        .addOption(
            OptionType.BOOLEAN,
            OPTION_UWU,
            "Uwuwify the fav"
        )
        .addOption(
            OptionType.BOOLEAN,
            OPTION_SPONGE,
            "Spongeifiy the fav"
        )

suspend fun executeMysteryFavCommand(storage: Storage, event: SlashCommandInteractionEvent) {
    val guildIds = event.jda.guilds.map { it.id }

    val userId = when (event.getOption(SELF)?.asBoolean) {
        true -> event.user.id
        else -> null
    }

    val interaction = event.reply("Fetching candidate...").await()

    val candidates = storage
        .getFavs(userId, event.guild?.id, emptyList())
        .filter { guildIds.contains(it.guildId) }

    val fav = candidates.weightedRandom()
        ?: return interaction.replyError("No favs found")

    val guild = event.jda.guilds
        .firstOrNull { it.id == fav.guildId }
        ?: return interaction.replyError("Guild not found:\n${fav.guildUrl()}", fav.id)
    val channel = guild.getTextChannelById(fav.channelId)
        ?: guild.getThreadChannelById(fav.channelId)
        ?: return interaction.replyError("Channel not found:\n${fav.channelUrl()}", fav.id)

    val message = retrieveMessageWithErrorHandling(fav, storage, interaction, channel) ?: return

    val sponge = event.getOption(OPTION_SPONGE)?.asBoolean == true
    val uwu = event.getOption(OPTION_UWU)?.asBoolean == true
    val embed = EmbedBuilder().forMessage(message, fav.id, sponge, uwu)
        .setAuthor("Mystery Fav", message.jumpUrl)
        .build()

    interaction.editOriginal(getFavMessage()).await()
    interaction.editOriginalEmbeds(embed).await()

    val original = interaction.retrieveOriginal().await()
    original.addReaction("👍").await()
    original.addReaction("👎").await()
}
