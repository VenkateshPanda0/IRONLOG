package app.ironlog.personal.data.seed

import app.ironlog.personal.platform.AssetReader

import app.ironlog.personal.data.transaction
import app.ironlog.personal.data.db.IronlogDatabase
import app.ironlog.personal.data.db.ProgramDayEntity
import app.ironlog.personal.data.db.ProgramDayExerciseEntity
import app.ironlog.personal.data.db.ProgramEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class ProgramSeedLoader(
    private val assets: AssetReader,
    private val database: IronlogDatabase,
) {
    suspend fun load(): Int = database.transaction {
        val source = assets.readText(PROGRAM_ASSET)
        val programs = Json.decodeFromString<List<ProgramSeed>>(source)
        val dao = database.dao()
        val exerciseIds = dao.allExercises().mapTo(mutableSetOf()) { it.id }
        validate(programs, exerciseIds)
        var inserted = 0
        programs.forEach { seed ->
            val existing = dao.programByName(seed.name)?.takeIf { it.isBuiltIn }
            if (existing != null) {
                val existingDays = dao.days(existing.id)
                val matches =
                    existingDays.size == seed.days.size &&
                        existingDays.zip(seed.days).all { (storedDay, sourceDay) ->
                            storedDay.name == sourceDay.name &&
                                dao.prescriptions(storedDay.id).map { prescription ->
                                    listOf(
                                        prescription.exerciseId,
                                        prescription.targetSets,
                                        prescription.repMin,
                                        prescription.repMax,
                                        prescription.restSeconds,
                                    )
                                } ==
                                    sourceDay.exercises.map { exercise ->
                                        listOf(
                                            exercise.id,
                                            exercise.sets,
                                            exercise.repMin,
                                            exercise.repMax,
                                            exercise.restSeconds,
                                        )
                                    }
                        }
                if (matches) return@forEach
                dao.deleteProgramDays(existing.id)
                dao.updateProgram(
                    existing.copy(
                        description = seed.description,
                        daysPerWeek = seed.daysPerWeek,
                    )
                )
            }
            val programId =
                existing?.id
                    ?: dao.addProgram(
                        ProgramEntity(
                            name = seed.name,
                            description = seed.description,
                            daysPerWeek = seed.daysPerWeek,
                            isBuiltIn = true,
                        )
                    )
            if (programId == -1L) return@forEach
            seed.days.forEachIndexed { dayIndex, day ->
                val dayId =
                    dao.addProgramDay(
                        ProgramDayEntity(
                            programId = programId,
                            dayIndex = dayIndex + 1,
                            name = day.name,
                        )
                    )
                day.exercises.forEachIndexed { orderIndex, exercise ->
                    dao.addPrescription(
                        ProgramDayExerciseEntity(
                            programDayId = dayId,
                            exerciseId = exercise.id,
                            orderIndex = orderIndex,
                            targetSets = exercise.sets,
                            repMin = exercise.repMin,
                            repMax = exercise.repMax,
                            restSeconds = exercise.restSeconds,
                        )
                    )
                }
            }
            inserted += 1
        }
        inserted
    }

    fun validate(programs: List<ProgramSeed>, exerciseIds: Set<String>): Unit {
        require(programs.map { it.name }.distinct().size == programs.size) {
            "Program seed contains duplicate names"
        }
        programs.forEach { program ->
            require(program.days.isNotEmpty()) { "${program.name} has no days" }
            require(program.daysPerWeek in 2..6) { "${program.name} has invalid frequency" }
            require(program.days.map { it.name }.distinct().size == program.days.size) {
                "${program.name} contains duplicate day names"
            }
            program.days.forEach { day ->
                require(day.name.isNotBlank()) { "${program.name} contains a blank day name" }
                require(day.exercises.isNotEmpty()) {
                    "${program.name}/${day.name} has no exercises"
                }
                day.exercises.forEach { exercise ->
                    require(exercise.id in exerciseIds) {
                        "${program.name}/${day.name} references missing exercise ${exercise.id}"
                    }
                    require(
                        exercise.sets in 1..8 &&
                            exercise.repMin in 1..30 &&
                            exercise.repMax in exercise.repMin..30 &&
                            exercise.restSeconds in 15..600
                    ) {
                        "${program.name}/${day.name} has an invalid prescription"
                    }
                }
                require(day.exercises.size <= 8) {
                    "${program.name}/${day.name} exceeds the supported exercise count"
                }
            }
        }
    }

    @Serializable
    data class ProgramSeed(
        val name: String,
        val description: String,
        val daysPerWeek: Int,
        val goal: String,
        val days: List<DaySeed>,
    )

    @Serializable data class DaySeed(val name: String, val exercises: List<ExerciseSeed>)

    @Serializable
    data class ExerciseSeed(
        val id: String,
        val sets: Int,
        val repMin: Int,
        val repMax: Int,
        val restSeconds: Int,
    )

    private companion object {
        const val PROGRAM_ASSET = "seed/programs.json"
    }
}
