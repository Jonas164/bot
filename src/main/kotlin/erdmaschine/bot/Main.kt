package erdmaschine.bot

import erdmaschine.bot.listener.*
import erdmaschine.bot.model.KeywordNotifier
import erdmaschine.bot.model.Storage
import erdmaschine.bot.reddit.RedditFacade
import erdmaschine.bot.reddit.RedditIntegrationRunner
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.cache.CacheFlag
import org.slf4j.LoggerFactory
import java.time.Clock
import kotlin.time.ExperimentalTime

@ExperimentalTime
fun main() = runBlocking {
    val env = Env()
    val log = LoggerFactory.getLogger("erdmaschine.bot.Main")!!
    //val redditIntegrationRunner = RedditIntegrationRunner(env)
    val statusCheck = StatusCheck(env)

    try {
        log.info("Starting up erdmaschine-bot")

        val storage = Storage(env)
        val keywordNotifier = KeywordNotifier(storage);
        val redditFacade = RedditFacade(env, Clock.systemDefaultZone())
        val commandListener = CommandListener(storage, redditFacade)
        val favReactionListener = FavReactionListener(storage)
        val emojiCountListener = EmojiCountListener(storage)
        val messageListener = MessageListener(storage)
        val publicMessageListener = PublicMessageListener(keywordNotifier)

        val jda = JDABuilder.createDefault(env.authToken)
            .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGE_REACTIONS)
            .addEventListeners(favReactionListener, emojiCountListener, messageListener, commandListener, publicMessageListener)
            .enableCache(CacheFlag.EMOJI)
            .build()

        jda.awaitReady()

        commandListener.initCommands(jda, env)
        //redditIntegrationRunner.run(storage, redditFacade, jda)
        statusCheck.run(storage, redditFacade, jda)

        jda.presence.setPresence(Activity.watching("out for hot takes"), false)
        log.info("Ready for action")
    } catch (e: Exception) {
        statusCheck.stop()
        //redditIntegrationRunner.stop()
        log.error(e.message, e)
    }
}
