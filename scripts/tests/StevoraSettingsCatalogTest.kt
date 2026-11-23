package io.homeassistant.companion.android.stevora

fun main() {
    val adminPages = setOf(StevoraSettingsPage.Management, StevoraSettingsPage.Devices,
        StevoraSettingsPage.Rooms, StevoraSettingsPage.Automation, StevoraSettingsPage.Assistants, StevoraSettingsPage.Connection)
    for (page in StevoraSettingsPage.entries) {
        check(page.accessible(true, true))
        check(page.accessible(true, false) == (page !in adminPages))
        check(page.accessible(false, true) == (page in setOf(StevoraSettingsPage.Home, StevoraSettingsPage.Privacy)))
        var cursor = page
        repeat(StevoraSettingsPage.entries.size) { cursor = cursor.parent }
        check(cursor == StevoraSettingsPage.Home)
    }
    val rooms = (1..1000).map { StevoraChoice("light.id_$it", "Лампа", "Стая $it") }
    check(StevoraChoice.matching(rooms, " ").size == rooms.size)
    check(StevoraChoice.matching(rooms, "  ЛАМПА   id_1000 ").single() == rooms.last())
    check(StevoraChoice.matching(rooms, "стая 1000").single() == rooms.last())
    check(StevoraChoice.matching(rooms, "never-present").isEmpty())
    println("PASS: settings access, back navigation and full-inventory search")
}
