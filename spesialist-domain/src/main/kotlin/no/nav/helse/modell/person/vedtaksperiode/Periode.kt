package no.nav.helse.modell.person.vedtaksperiode

import java.time.LocalDate

class Periode(
    private val fom: LocalDate,
    private val tom: LocalDate,
) {
    init {
        require(fom <= tom) { "Fom kan ikke være etter tom" }
    }

    fun fom() = fom

    fun tom() = tom

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is Periode &&
                javaClass == other.javaClass &&
                fom == other.fom &&
                tom == other.tom
        )

    fun overlapperMed(other: Periode) = this.overlapper(other) || other.overlapper(this)

    private fun overlapper(other: Periode) = other.fom in fom..tom || other.tom in fom..tom

    override fun hashCode(): Int {
        var result = fom.hashCode()
        result = 31 * result + tom.hashCode()
        return result
    }

    override fun toString(): String {
        return "Periode(fom=$fom, tom=$tom)"
    }

    companion object {
        infix fun LocalDate.til(other: LocalDate) = Periode(this, other)
    }
}
