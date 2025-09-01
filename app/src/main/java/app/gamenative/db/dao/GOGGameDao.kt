package app.gamenative.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.gamenative.data.GOGGame
import kotlinx.coroutines.flow.Flow

@Dao
interface GOGGameDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(game: GOGGame)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(games: List<GOGGame>)

    @Update
    suspend fun update(game: GOGGame)

    @Delete
    suspend fun delete(game: GOGGame)

    @Query("DELETE FROM gog_games WHERE id = :gameId")
    suspend fun deleteById(gameId: String)

    @Query("SELECT * FROM gog_games WHERE id = :gameId")
    suspend fun getById(gameId: String): GOGGame?

    @Query("SELECT * FROM gog_games ORDER BY title ASC")
    fun getAll(): Flow<List<GOGGame>>

    @Query("SELECT * FROM gog_games ORDER BY title ASC")
    suspend fun getAllAsList(): List<GOGGame>

    @Query("SELECT * FROM gog_games WHERE isInstalled = :isInstalled ORDER BY title ASC")
    fun getByInstallStatus(isInstalled: Boolean): Flow<List<GOGGame>>

    @Query("SELECT * FROM gog_games WHERE title LIKE '%' || :searchQuery || '%' ORDER BY title ASC")
    fun searchByTitle(searchQuery: String): Flow<List<GOGGame>>

    @Query("DELETE FROM gog_games")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM gog_games")
    fun getCount(): Flow<Int>

    @Transaction
    suspend fun replaceAll(games: List<GOGGame>) {
        deleteAll()
        insertAll(games)
    }
}
