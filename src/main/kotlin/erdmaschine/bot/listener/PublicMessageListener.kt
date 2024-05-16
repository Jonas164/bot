package erdmaschine.bot.listener

import erdmaschine.bot.model.KeywordNotifier
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

class PublicMessageListener(private val keywordNotifier: KeywordNotifier) : ListenerAdapter()
{
    override fun onMessageReceived(event: MessageReceivedEvent) {
        keywordNotifier.checkAndNotifyKeywords(event);
    }
}