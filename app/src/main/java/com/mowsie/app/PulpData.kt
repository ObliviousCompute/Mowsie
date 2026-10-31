package com.mowsie.app

/**
 * Design-only, entirely synthetic presentation data. These records never enter
 * GenesisStore, Soulcrum, local balances, networking, or transfer authorization.
 * Replace this provider with independently validated Field evidence later.
 */
internal object PulpData {
    internal data class FieldEntry(val Name: String, val Balance: String, val Currency: String)
    internal data class Record(val Status: String, val Peer: String, val Value: String, val Time: String)

    val Fields = listOf(
        FieldEntry("Tia's Tacos", "128,400.00 Taco Tips", "Taco Tips"),
        FieldEntry("Sally's Coffee", "2,750.20 Beans", "Beans"),
        FieldEntry("Bob's Donuts", "10,485.75 Salt", "Salt"),
        FieldEntry("In-N-Out Burger", "8,430.50 Salt", "Salt"),
        FieldEntry("City Wok", "3,750.00 Salt", "Salt"),
        FieldEntry("Joe's FroYo", "1,234.25 Salt", "Salt"),
        FieldEntry("Farmers Market", "530.80 Produce", "Produce"),
        FieldEntry("Dunder Mifflin", "12,340.00 Paper", "Paper"),
        FieldEntry("Moonlight Arcade", "890.75 Tokens", "Tokens"),
        FieldEntry("Burger Barn", "4,120.00 Burger Bucks", "Burger Bucks"),
        FieldEntry("The Corner Bakery", "310.20 Crumbs", "Crumbs"),
        FieldEntry("Garden Club", "1,408.00 Seeds", "Seeds")
    )

    val Peers = listOf(
        "Ricardo", "Alice", "Milton", "Peter", "Samir", "Michael",
        "Joanna", "Clementine", "Rosa", "Theo", "Lumbergh", "Nina",
        "Mira", "Ellis", "Frank", "Wendy", "Max", "Jules", "Daria", "Sasha"
    )

    val Lanterns = listOf(
        "Default", "Personal", "Cryodine", "Initech", "Enron", "Black Mesa",
        "Aperture", "Moonbase", "Sunflower", "Night Shift", "Cinder", "Beacon Hill",
        "Blue Lantern", "Orchard", "Horizon"
    )

    /** Stable synthetic rows vary with the selected Field, not with device state. */
    fun Records(Field: FieldEntry): List<Record> {
        val Seed = Fields.indexOfFirst { it.Name == Field.Name }.coerceAtLeast(0)
        val Statuses = listOf("Received", "Sent", "Admitted", "Rejected", "Received", "Sent", "Admitted")
        val Amounts = listOf("12.50", "4.00", "25.00", "8.75", "50.00", "2.25", "19.90", "6.00", "40.00", "1.50")
        val RejectReasons = listOf("Insufficient evidence", "Invalid signature", "Conflicting claim", "Unrecognized Field")
        return List(42) { Index ->
            val Status = Statuses[(Index + Seed * 3) % Statuses.size]
            val Peer = Peers[(Index * 7 + Seed * 5) % Peers.size]
            val Amount = Amounts[(Index * 3 + Seed) % Amounts.size]
            val Value = when (Status) {
                "Received" -> "+$Amount ${Field.Currency}"
                "Sent" -> "−$Amount ${Field.Currency}"
                "Admitted" -> "$Amount ${Field.Currency}"
                else -> RejectReasons[(Index + Seed) % RejectReasons.size]
            }
            val MinutesAgo = Index * 7 + (Seed * 3)
            val Time = if (MinutesAgo < 60) "${MinutesAgo + 1}m" else "${(MinutesAgo / 60)}h ${MinutesAgo % 60}m"
            Record(Status, Peer, Value, Time)
        }
    }
}
