package app.ironlog.personal.data.repo

import kotlinx.datetime.LocalDate
import app.ironlog.personal.time.*

import app.ironlog.personal.data.transaction
import app.ironlog.personal.data.db.BodyMeasurementEntity
import app.ironlog.personal.data.db.IronlogDatabase
import app.ironlog.personal.data.db.PhysiqueScanEntity
import app.ironlog.personal.domain.BodyProportions
import app.ironlog.personal.domain.PhysiqueType
import okio.Path
import app.ironlog.personal.platform.appFileSystem
import app.ironlog.personal.platform.ioDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Physique goal and tape-measure checks against it. */
class PhysiqueRepository(private val db: IronlogDatabase, private val legacyPhotoDir: Path) {
    private val dao = db.dao()

    val scans: Flow<List<PhysiqueScanEntity>> = dao.physiqueScans()

    suspend fun setGoal(type: PhysiqueType) = dao.setPhysiqueGoal(type.name)

    /**
     * Saves a check and the same circumferences as a body measurement, so the Body trend and the
     * physique history never disagree.
     */
    suspend fun save(p: BodyProportions, goal: PhysiqueType, match: Int, date: LocalDate = LocalDate.now()) =
        db.transaction {
            dao.addMeasurement(BodyMeasurementEntity(date = date.toString(), shouldersCm = p.shouldersCm, waistCm = p.waistCm, hipsCm = p.hipsCm, thighCm = p.thighCm))
            dao.addPhysiqueScan(
                PhysiqueScanEntity(
                    date = date.toString(),
                    fileName = "",
                    goal = goal.name,
                    shoulder = p.shouldersCm,
                    waist = p.waistCm,
                    hip = p.hipsCm,
                    leftThigh = p.thighCm,
                    rightThigh = p.thighCm,
                    height = p.heightCm ?: 0.0,
                    legToTorso = 0.0,
                    matchScore = match,
                )
            )
        }

    suspend fun delete(scan: PhysiqueScanEntity) = dao.deletePhysiqueScan(scan.id)

    suspend fun deleteAll() {
        dao.deleteAllPhysiqueScans()
        // Photos from the earlier photo-based check, if any are left on the phone.
        withContext(ioDispatcher) { appFileSystem.deleteRecursively(legacyPhotoDir, mustExist = false) }
    }

    companion object {
        /** Checks saved before tape measuring were estimated from photos (widths in pixels). */
        fun isPhotoEstimate(scan: PhysiqueScanEntity) = scan.fileName.isNotEmpty()

        fun proportions(scan: PhysiqueScanEntity) =
            BodyProportions(scan.shoulder, scan.waist, scan.hip, (scan.leftThigh + scan.rightThigh) / 2, scan.height.takeIf { it > 0 && !isPhotoEstimate(scan) })
    }
}
