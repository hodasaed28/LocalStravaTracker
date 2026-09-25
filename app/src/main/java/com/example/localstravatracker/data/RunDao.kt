package com.example.localstravatracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RunDao {
    @Insert
    suspend fun insertRun(run: Run): Long

    @Insert
    suspend fun insertPoints(points: List<TrackPoint>)

    @Query("SELECT * FROM runs ORDER BY timestamp DESC")
    fun getAllRuns(): Flow<List<Run>>

    @Query("SELECT * FROM track_points WHERE runId = :runId ORDER BY timestamp ASC")
    suspend fun getPointsForRun(runId: Long): List<TrackPoint>

    @Query("DELETE FROM runs WHERE id = :runId")
    suspend fun deleteRun(runId: Long)
}