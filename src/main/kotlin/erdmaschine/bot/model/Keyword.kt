package erdmaschine.bot.model

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table

data class Keyword (val keyword: String, val watchingUserIds: List<String>){

    companion object {

        @JvmStatic
        fun fromResultRow(resultRow: ResultRow) =
            Keyword(
                resultRow[Keywords.keyword].toString(),
                resultRow[Keywords.userIds]
            )
    }
}


object Keywords : Table() {
    val id = integer("id").autoIncrement()
    val keyword = varchar("keyword",100)
    val userIds = array<String>("userIds", 10000)

    override val primaryKey = PrimaryKey(id)
}