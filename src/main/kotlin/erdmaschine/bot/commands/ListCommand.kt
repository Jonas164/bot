package erdmaschine.bot.commands

import erdmaschine.bot.await
import erdmaschine.bot.model.Storage
import erdmaschine.bot.replyError
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands

private const val OPTION_TAG = "tag"
private const val OPTION_GUILD = "server"

val ListCommand = Commands.slash("list", "List amount of favs per tag")
    .addOption(
        OptionType.STRING,
        OPTION_TAG,
        "Limit the listed counts to favs with at least one of these (space-separated) tags"
    )
    .addOption(
        OptionType.STRING,
        OPTION_GUILD,
        "Limit the listed counts to favs from a single server (provided the server id)"
    )

suspend fun executeListCommand(storage: Storage, event: SlashCommandInteractionEvent) {
    val interaction = event.reply("Fetching favs...").await()

    val tags = event.getOption(OPTION_TAG)?.asString.orEmpty().split(" ").filter { it.isNotBlank() }
    val guildId = event.getOption(OPTION_GUILD)?.asString ?: event.guild?.id
    val favs = storage.getFavs(event.user.id, guildId, tags)

    if (favs.isEmpty()) {
        return interaction.replyError("No favs found")
    }

    val tagCount = mutableMapOf<String, Int>()
    favs
        .forEach { fav ->
            fav.tags.forEach {
                val count = tagCount[it] ?: 0
                tagCount[it] = count + 1
            }
        }

    interaction.editOriginal("Listing total of ${tagCount.size} tags").await()

    tagCount
        .entries
        .sortedBy { it.key }
        .chunked(25)
        .chunked(10)
        .forEach { messageEntries ->
            val embeds = mutableListOf<MessageEmbed>()
            messageEntries.forEach { fields ->
                val builder = EmbedBuilder()
                fields.forEach { builder.addField(it.key, it.value.toString(), true) }
                embeds.add(builder.build())
            }
            event
                .channel
                .sendMessage(MessageBuilder().setEmbeds(embeds).build())
                .await()
        }
}
