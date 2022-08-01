package erdmaschine.bot.listener

import erdmaschine.bot.Env
import erdmaschine.bot.await
import erdmaschine.bot.commands.*
import erdmaschine.bot.model.Storage
import erdmaschine.bot.replyError
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.slf4j.LoggerFactory
import kotlin.time.ExperimentalTime

private val slashCommandData = listOf(
    HelpCommand,
    ListCommand,
    FavCommand,
    QuoteCommand,
    StatsCommand,
    GuildStatsCommand,
    MysteryFavCommand,
)

@ExperimentalTime
class CommandListener(
    private val storage: Storage
) : ListenerAdapter() {

    private val log = LoggerFactory.getLogger(this::class.java)!!

    suspend fun initCommands(jda: JDA, env: Env) {
        if (env.deployCommandsGobal == "true") {
            log.info("Initializing commands globally")
            jda.updateCommands().addCommands(slashCommandData).await()
        } else {
            jda.guilds.forEach {
                log.info("Initializing commands on [$it]")
                it.updateCommands().addCommands(slashCommandData).await()
            }
        }
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) = runBlocking {
        try {
            when (event.name) {
                HelpCommand.name -> executeHelpCommand(event)
                ListCommand.name -> executeListCommand(storage, event)
                FavCommand.name -> executeFavCommand(storage, event)
                QuoteCommand.name -> executeQuoteCommand(event)
                StatsCommand.name -> executeStatsCommand(storage, event)
                GuildStatsCommand.name -> executeGuildStats(storage, event)
                MysteryFavCommand.name -> executeMysteryFavCommand(storage, event)
                else -> Unit
            }
        } catch (e: Exception) {
            val interaction = when (event.isAcknowledged) {
                true -> event.hook
                else -> event.reply("Whoopsie (╯°□°）╯︵ ┻━┻").await()
            }
            interaction.replyError(e.message ?: "Unknown error")
            log.error(e.message, e)
        }
    }
}
