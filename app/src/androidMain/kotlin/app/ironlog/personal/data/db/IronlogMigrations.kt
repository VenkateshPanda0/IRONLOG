package app.ironlog.personal.data.db

/**
 * Upgrades for databases created by earlier Android versions of the app (v1 to v9). The iOS app
 * starts at the current version, so these live with the Android code.
 */
object IronlogMigrations {
        /** v2: exercise swap tracking on session exercises and the progress photo table. */
        val MIGRATION_1_2 =
            object : androidx.room.migration.Migration(1, 2) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE session_exercise ADD COLUMN originalExerciseId TEXT")
                    db.execSQL("ALTER TABLE session_exercise ADD COLUMN originalNameSnapshot TEXT")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS progress_photo (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, date TEXT NOT NULL, " +
                            "fileName TEXT NOT NULL, note TEXT NOT NULL, createdAt INTEGER NOT NULL)"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_progress_photo_date ON progress_photo (date)")
                }
            }

        /** v9: supersets in workouts. */
        val MIGRATION_8_9 =
            object : androidx.room.migration.Migration(8, 9) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE session_exercise ADD COLUMN supersetGroup INTEGER")
                }
            }

        /** v8: shoulder circumference, for tape-measure physique checks. */
        val MIGRATION_7_8 =
            object : androidx.room.migration.Migration(7, 8) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE body_measurement ADD COLUMN shouldersCm REAL")
                }
            }

        /** v7: Health Connect record IDs on imported weigh-ins and cardio sessions. */
        val MIGRATION_6_7 =
            object : androidx.room.migration.Migration(6, 7) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE body_weight ADD COLUMN healthId TEXT")
                    db.execSQL("ALTER TABLE cardio_session ADD COLUMN healthId TEXT")
                }
            }

        /** v6: marks steps and sleep imported from Health Connect, so manual entries are never overwritten. */
        val MIGRATION_5_6 =
            object : androidx.room.migration.Migration(5, 6) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE daily_log ADD COLUMN stepsFromHealth INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE daily_log ADD COLUMN sleepFromHealth INTEGER NOT NULL DEFAULT 0")
                }
            }

        /** v5: physique goal on the profile and the physique photo check history. */
        val MIGRATION_4_5 =
            object : androidx.room.migration.Migration(4, 5) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE user_profile ADD COLUMN physiqueGoal TEXT NOT NULL DEFAULT ''")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS physique_scan (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, date TEXT NOT NULL, fileName TEXT NOT NULL, " +
                            "goal TEXT NOT NULL, shoulder REAL NOT NULL, waist REAL NOT NULL, hip REAL NOT NULL, " +
                            "leftThigh REAL NOT NULL, rightThigh REAL NOT NULL, height REAL NOT NULL, " +
                            "legToTorso REAL NOT NULL, matchScore INTEGER NOT NULL, createdAt INTEGER NOT NULL)"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_physique_scan_date ON physique_scan (date)")
                }
            }

        /** v4: cuisine and popularity on foods for Indian-first search and cuisine filters. */
        val MIGRATION_3_4 =
            object : androidx.room.migration.Migration(3, 4) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE food ADD COLUMN cuisine TEXT")
                    db.execSQL("ALTER TABLE food ADD COLUMN popularity INTEGER NOT NULL DEFAULT 0")
                }
            }

        /** v3: cardio, daily log (steps, water, sleep, check-in), habits, measurements, wellness goals. */
        val MIGRATION_2_3 =
            object : androidx.room.migration.Migration(2, 3) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE goal ADD COLUMN stepGoal INTEGER NOT NULL DEFAULT 8000")
                    db.execSQL("ALTER TABLE goal ADD COLUMN waterGoalMl INTEGER NOT NULL DEFAULT 3000")
                    db.execSQL("ALTER TABLE goal ADD COLUMN sleepGoalHours REAL NOT NULL DEFAULT 8.0")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS cardio_session (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, date TEXT NOT NULL, " +
                            "type TEXT NOT NULL, durationMin REAL NOT NULL, distanceKm REAL, calories INTEGER, avgHeartRate INTEGER, " +
                            "notes TEXT NOT NULL, createdAt INTEGER NOT NULL)"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_cardio_session_date ON cardio_session (date)")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS daily_log (date TEXT NOT NULL, steps INTEGER, waterMl INTEGER NOT NULL, " +
                            "sleepHours REAL, sleepQuality INTEGER, energy INTEGER, soreness INTEGER, stress INTEGER, mood INTEGER, PRIMARY KEY(date))"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS habit (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, " +
                            "isActive INTEGER NOT NULL, createdAt INTEGER NOT NULL)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS habit_check (habitId INTEGER NOT NULL, date TEXT NOT NULL, PRIMARY KEY(habitId, date), " +
                            "FOREIGN KEY(habitId) REFERENCES habit(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_habit_check_date ON habit_check (date)")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS body_measurement (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, date TEXT NOT NULL, " +
                            "waistCm REAL, chestCm REAL, armCm REAL, thighCm REAL, hipsCm REAL, neckCm REAL, bodyFatPct REAL, createdAt INTEGER NOT NULL)"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_body_measurement_date ON body_measurement (date)")
                }
            }

    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
}
