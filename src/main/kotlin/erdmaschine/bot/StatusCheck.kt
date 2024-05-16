package erdmaschine.bot

import erdmaschine.bot.model.Storage
import erdmaschine.bot.model.Sub
import erdmaschine.bot.reddit.RedditFacade
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.JDA
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path

class StatusCheck(env: Env) {
    private val log = LoggerFactory.getLogger(this::class.java)!!
    private val scheduler = Executors.newScheduledThreadPool(1)
    private val interval = 5000L
    private val file = Path(env.statusFile).toFile()

    fun run(storage: Storage, redditFacade: RedditFacade, jda: JDA) {
        if (!file.exists() && !file.createNewFile()) {
            log.error("Cannot create status file '$file'")
            return
        }
        if (file.exists() && !file.canWrite()) {
            log.error("Cannot write status file '$file'")
        }
        scheduler.scheduleAtFixedRate(kotlinx.coroutines.Runnable {
            runBlocking {
                try {
                    log.info("Performing status check")
                    storage.check()
                    redditFacade.check()
                    jda.check()
                    file.writeText("OK")
                } catch (ex: Exception) {
                    log.error("Could not check status", ex)
                }
            }
        }, interval, interval, TimeUnit.MILLISECONDS)
    }

    fun stop() {
        scheduler.shutdown()
    }

    private suspend fun Storage.check() {
        getFavs(null, null, emptySet())
        getEmojiUsages(null)
        getSubs()
    }

    private fun RedditFacade.check() {
        return;
        //fetch(setOf(Sub("", "", "all", "top", false)))
    }

    private fun JDA.check() {
        if (status != JDA.Status.CONNECTED) {
            throw Exception("JDA stauts is '$status'")
        }
    }
}
