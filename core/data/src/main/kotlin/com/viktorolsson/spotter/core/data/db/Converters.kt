package com.viktorolsson.spotter.core.data.db

import androidx.room.TypeConverter
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.Limitation
import com.viktorolsson.spotter.core.model.Muscle
import java.time.Instant
import java.time.LocalDate

/**
 * Enums are stored by name (Room's default). Enum collections are stored as a
 * comma-separated list of names, which keeps rows readable in the DB inspector.
 */
class Converters {
    @TypeConverter fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun epochMillisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter fun localDateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()
    @TypeConverter fun epochDayToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter fun musclesToString(value: List<Muscle>): String = value.joinNames()
    @TypeConverter fun stringToMuscles(value: String): List<Muscle> = value.splitNames(Muscle::valueOf)

    @TypeConverter fun muscleSetToString(value: Set<Muscle>): String = value.joinNames()
    @TypeConverter fun stringToMuscleSet(value: String): Set<Muscle> = value.splitNames(Muscle::valueOf).toSet()

    @TypeConverter fun equipmentToString(value: List<Equipment>): String = value.joinNames()
    @TypeConverter fun stringToEquipment(value: String): List<Equipment> = value.splitNames(Equipment::valueOf)

    @TypeConverter fun equipmentSetToString(value: Set<Equipment>): String = value.joinNames()
    @TypeConverter fun stringToEquipmentSet(value: String): Set<Equipment> = value.splitNames(Equipment::valueOf).toSet()

    @TypeConverter fun limitationSetToString(value: Set<Limitation>): String = value.joinNames()
    @TypeConverter fun stringToLimitationSet(value: String): Set<Limitation> = value.splitNames(Limitation::valueOf).toSet()
}

private fun Iterable<Enum<*>>.joinNames(): String = joinToString(",") { it.name }

private fun <T> String.splitNames(parse: (String) -> T): List<T> =
    if (isEmpty()) emptyList() else split(',').map(parse)
