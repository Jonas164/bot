package erdmaschine.bot.model

import com.zaxxer.hikari.HikariDataSource
import erdmaschine.bot.Env
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import javax.sql.DataSource

class Storage(env: Env) {

    private val log = LoggerFactory.getLogger(this::class.java)!!
    private val dataSource = HikariDataSource()
        .also {
            it.connectionTimeout = 30_000
            it.jdbcUrl = env.dbUrl
        }
    private val postHistoryThreshold = env.redditPostHistoryThreshold.ifEmpty { "168" }.toLong()

    init {
        transaction(Database.connect(dataSource)) {
            addLogger(Slf4jSqlDebugLogger)
            SchemaUtils.createMissingTablesAndColumns(
                Favs, PostHistory, Subs, EmojiUsages,
                inBatch = true, withLogs = true
            )
        }
    }

    suspend fun saveNewFav(
        userId: String,
        guildId: String,
        channelId: String,
        messageId: String,
        authorId: String
    ): String {
        log.info("Creating fav for User[$userId] Guild[$guildId] Channel[$channelId] Message[$messageId]")
        val favId = query(dataSource) {
            Favs.insertIgnore {
                it[Favs.userId] = userId
                it[Favs.guildId] = guildId
                it[Favs.channelId] = channelId
                it[Favs.messageId] = messageId
                it[Favs.authorId] = authorId
                it[used] = 0
                it[votes] = 0
                it[tags] = ""
            } get Favs.id
        }

        return favId.toString()
    }

    suspend fun getFavs(userId: String?, guildId: String?, tags: Collection<String>): List<Fav> {
        log.info("Getting favs for User[$userId] Guild[$guildId] Tags$tags")
        return query(dataSource) {
            val query = Favs.selectAll()
                .orderBy(Favs.used to SortOrder.DESC)

            if (!userId.isNullOrBlank()) {
                query.andWhere { Favs.userId eq userId }
            }

            if (!guildId.isNullOrBlank()) {
                query.andWhere { Favs.guildId eq guildId }
            }

            if (tags.isNotEmpty()) {
                query.andWhere {
                    tags.map {
                        Op.build { Favs.tags like "% ${it.lowercase()} %" }
                    }.compoundOr()
                }
            }

            query.map { Fav.fromResultRow(it) }
        }
    }

    suspend fun removeFav(favId: String) {
        val id = favId.toIntOrNull() ?: return
        log.info("Removing Fav[$id]")
        query(dataSource) {
            Favs.deleteWhere { Favs.id eq id }
        }
    }

    suspend fun writeTags(favId: String, tags: Collection<String>) {
        val id = favId.toIntOrNull() ?: return
        val writeTags = tags.map { it.lowercase().trim() }
        log.info("Setting tags of Fav[$id] to $writeTags")
        query(dataSource) {
            Favs.update({ Favs.id eq id }) {
                it[Favs.tags] = " " + writeTags.joinToString(" ") + " "
            }
        }
    }

    suspend fun getFav(favId: String): Fav? {
        val id = favId.toIntOrNull() ?: return null
        log.info("Fetching Fav[$id]")
        return query(dataSource) {
            Favs.select { Favs.id eq id }
                .map { Fav.fromResultRow(it) }
                .firstOrNull()
        }
    }

    suspend fun increaseUsed(fav: Fav) {
        val id = fav.id.toIntOrNull() ?: return
        log.info("Increasing used count of Fav[$id]")
        query(dataSource) {
            Favs.update({ Favs.id eq id }) {
                it[used] = fav.used + 1
            }
        }
    }

    suspend fun upvote(fav: Fav) {
        val id = fav.id.toIntOrNull() ?: return
        log.info("Upvoting Fav[$id]")
        query(dataSource) {
            Favs.update({ Favs.id eq id }) {
                it[votes] = fav.votes + 1
            }
        }
    }

    suspend fun downvote(fav: Fav) {
        val id = fav.id.toIntOrNull() ?: return
        log.info("Downvoting Fav[$id]")
        query(dataSource) {
            Favs.update({ Favs.id eq id }) {
                it[votes] = fav.votes - 1
            }
        }
    }

    suspend fun addSub(guildId: String, channelId: String, sub: String, listing: String, nsfw: Boolean): Sub? {
        log.info("Adding Sub[$sub/$listing] on [G:$guildId] in [C:$channelId]")
        return query(dataSource) {
            val row = Subs.insertIgnore {
                it[Subs.guildId] = guildId
                it[Subs.channelId] = channelId
                it[Subs.sub] = sub
                it[Subs.listing] = listing
                it[Subs.nsfw] = nsfw
            }.resultedValues?.first()
            row?.let {
                Sub.fromResultRow(row)
            }
        }
    }

    suspend fun removeSub(guildId: String, channelId: String, sub: String) {
        log.info("Removing Sub[$sub] on [G:$guildId] in [C:$channelId]")
        query(dataSource) {
            Subs.deleteWhere {
                (Subs.guildId eq guildId) and
                        (Subs.channelId eq channelId) and
                        (Subs.sub eq sub)
            }

            log.info("Removing post history for Sub[$sub] on [G:$guildId] in [C:$channelId]")
            PostHistory.deleteWhere {
                (PostHistory.guildId eq guildId) and
                        (PostHistory.channelId eq channelId) and
                        (PostHistory.sub eq sub)
            }
        }
    }

    suspend fun getSubs(): Collection<Sub> {
        return query(dataSource) {
            Subs.selectAll()
                .map { Sub.fromResultRow(it) }
        }
    }

    suspend fun isInPostHistory(guildId: String, channelId: String, sub: String, postId: String): Boolean {
        val whereExpression = (PostHistory.guildId eq guildId) and
                (PostHistory.channelId eq channelId) and
                (PostHistory.sub eq sub) and
                (PostHistory.postId eq postId)

        return query(dataSource) {
            PostHistory.update({ whereExpression }) {
                it[lastSeen] = Clock.System.now()
            }

            PostHistory.select(whereExpression).count() > 0
        }
    }

    suspend fun addPostHistory(guildId: String, channelId: String, sub: String, postId: String) {
        query(dataSource) {
            PostHistory.insertIgnore {
                it[PostHistory.guildId] = guildId
                it[PostHistory.channelId] = channelId
                it[PostHistory.sub] = sub
                it[PostHistory.postId] = postId
                it[lastSeen] = Clock.System.now()
            }
        }
    }

    suspend fun cleanupPostHistory() {
        val threshold = Clock.System.now().minus(postHistoryThreshold, DateTimeUnit.HOUR)
        query(dataSource) {
            val deleted = PostHistory.deleteWhere {
                lastSeen less threshold
            }
            if (deleted > 0) {
                log.info("Cleaned up $deleted post histories before ${threshold.toLocalDateTime(TimeZone.currentSystemDefault())}")
            }
        }
    }

    suspend fun getEmojiUsages(guildId: String?): List<EmojiUsage> {
        return query(dataSource) {
            val query = EmojiUsages.selectAll()

            if (guildId != null) {
                query.andWhere { EmojiUsages.guildId eq guildId }
            }

            query.orderBy(EmojiUsages.used to SortOrder.DESC)
                .limit(25)
                .map { EmojiUsage.fromResultRow(it) }
        }
    }

    suspend fun updateEmojiUsage(guildId: String, emojiName: String) {
        log.info("Updating emoji used of emoji [$emojiName] on guild [$guildId]")
        val whereExpression = (EmojiUsages.emojiName eq emojiName) and
                (EmojiUsages.guildId eq guildId)
        query(dataSource) {
            EmojiUsages.insertIgnore {
                it[EmojiUsages.guildId] = guildId
                it[EmojiUsages.emojiName] = emojiName
                it[used] = 0
            }
            EmojiUsages.update({ whereExpression }) {
                it.update(used, used + 1)
            }
        }
    }

    suspend fun getUsersForKeyword(guildId: String, keyword: String){
        val row = query(dataSource) {
            Keywords.select { Keywords.keyword eq keyword }
        }

        row.map { Keyword.fromResultRow(it) }
    }

    suspend fun addKeyword(guildId: String, keyword: String, userId: String){
        val row = query(dataSource) {
            Keywords.select { Keywords.keyword eq keyword }
        }

        row.map { Keyword.fromResultRow(it) }
    }

    private suspend fun <T> query(dataSource: DataSource, block: () -> T): T =
        withContext(Dispatchers.IO) {
            transaction(Database.connect(dataSource)) {
                addLogger(Slf4jSqlDebugLogger)
                block()
            }
        }
}
