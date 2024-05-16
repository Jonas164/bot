package erdmaschine.bot.commands

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands

val KeywordCommand = Commands.slash("keyword","notifies you when the provided keyword was found")
    .addOption(OptionType.STRING, "keyword", "Keyword that should be watched", true)


