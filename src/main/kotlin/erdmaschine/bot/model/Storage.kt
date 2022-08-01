package erdmaschine.bot.model

interface Storage {
    suspend fun saveNewFav(
        userId: String,
        guildId: String,
        channelId: String,
        messageId: String,
        authorId: String
    ): String

    suspend fun getFavs(userId: String?, guildId: String?, tags: Collection<String>): List<Fav>
    suspend fun removeFav(favId: String)
    suspend fun writeTags(favId: String, tags: Collection<String>)
    suspend fun getFav(favId: String): Fav?
    suspend fun increaseUsed(fav: Fav)
    suspend fun upvote(fav: Fav)
    suspend fun downvote(fav: Fav)
}
