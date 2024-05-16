package erdmaschine.bot.model

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.slf4j.LoggerFactory

class KeywordNotifier(private val storage: Storage){

    val log = LoggerFactory.getLogger("erdmaschine.bot.Main")!!

    fun checkAndNotifyKeywords(event: MessageReceivedEvent){
        println(event);
        storage.
    }
}