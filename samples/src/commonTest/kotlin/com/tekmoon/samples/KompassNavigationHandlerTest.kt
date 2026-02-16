package com.tekmoon.samples

import com.tekmoon.kompass.Destination
import kotlinx.serialization.Serializable
import kotlin.test.Test


@Serializable
private data object PreviewHome : Destination {
    override val id: String = "preview/home"
}

@Serializable
private data object PreviewDetails : Destination {
    override val id: String = "preview/details"
}

@Serializable
private data object PreviewProfile : Destination {
    override val id: String = "preview/profile"
}

@Serializable
private data object PreviewSettings : Destination {
    override val id: String = "preview/settings"
}

class KompassNavigationHandlerTest {

    // === Basic Navigation ===

    @Test
    fun navigate_and_pop() {
        val robot = NavigationHandlerTestRobot(PreviewHome)

        robot.navigate(PreviewDetails)
        robot.assertTop(PreviewDetails)
        robot.assertStackSize(2)

        robot.pop()
        robot.assertTop(PreviewHome)
        robot.assertStackSize(1)
    }

    @Test
    fun multiple_navigation() {
        val robot = NavigationHandlerTestRobot(PreviewHome)

        robot.navigate(PreviewDetails)
        robot.navigate(PreviewProfile)
        robot.navigate(PreviewSettings)

        robot.assertTop(PreviewSettings)
        robot.assertStackSize(4)
    }

    // === Pop Multiple ===

    @Test
    fun pop_multiple_entries() {
        val robot = NavigationHandlerTestRobot(PreviewHome)

        robot.navigate(PreviewDetails)
        robot.navigate(PreviewProfile)
        robot.navigate(PreviewSettings)
        robot.assertStackSize(4)

        robot.popCount(2)
        robot.assertTop(PreviewDetails)
        robot.assertStackSize(2)
    }

    @Test
    fun pop_multiple_more_than_available() {
        val robot = NavigationHandlerTestRobot(PreviewHome)

        robot.navigate(PreviewDetails)
        robot.navigate(PreviewProfile)
        robot.assertStackSize(3)

        // Try to pop 5 entries, but only 2 available (keeps at least 1)
        robot.popCount(5)
        robot.assertTop(PreviewHome)
        robot.assertStackSize(1)
    }

    // === Pop Until ===

    @Test
    fun pop_until_destination() {
        val robot = NavigationHandlerTestRobot(PreviewHome)

        robot.navigate(PreviewDetails)
        robot.navigate(PreviewProfile)
        robot.navigate(PreviewSettings)
        robot.assertStackSize(4)

        robot.popUntil(PreviewDetails.id)
        robot.assertTop(PreviewDetails)
        robot.assertStackSize(2)
    }

    @Test
    fun pop_until_non_existent_destination() {
        val robot = NavigationHandlerTestRobot(PreviewHome)

        robot.navigate(PreviewDetails)
        robot.navigate(PreviewProfile)
        robot.assertStackSize(3)

        // Trying to pop until non-existent destination should not change state
        robot.popUntil("non/existent")
        robot.assertTop(PreviewProfile)
        robot.assertStackSize(3)
    }

    // === PopUpTo ===

    @Test
    fun pop_up_to_exclusive() {
        val robot = NavigationHandlerTestRobot(PreviewHome)

        robot.navigate(PreviewDetails)
        robot.navigate(PreviewProfile)
        robot.navigate(PreviewSettings)
        robot.assertStackSize(4)

        robot.navigateWithPopUpTo(
            destination = PreviewProfile,
            popUpTo = PreviewDetails.id,
            inclusive = false
        )
        robot.assertTop(PreviewProfile)
        robot.assertStackSize(3) // Home + Details + Profile
    }

    @Test
    fun pop_up_to_inclusive() {
        val robot = NavigationHandlerTestRobot(PreviewHome)

        robot.navigate(PreviewDetails)
        robot.navigate(PreviewProfile)
        robot.navigate(PreviewSettings)
        robot.assertStackSize(4)

        robot.navigateWithPopUpTo(
            destination = PreviewProfile,
            popUpTo = PreviewDetails.id,
            inclusive = true
        )
        robot.assertTop(PreviewProfile)
        robot.assertStackSize(2) // Home + Profile (Details removed)
    }

    @Test
    fun pop_up_to_inclusive2() {
        val robot = NavigationHandlerTestRobot(PreviewHome)

        robot.navigate(PreviewDetails)
        robot.navigate(PreviewProfile)
        robot.navigate(PreviewSettings)
        robot.assertStackSize(4)

        robot.navigateWithPopUpTo(
            destination = PreviewProfile,
            popUpTo = PreviewHome.id,
            inclusive = true
        )
        robot.assertTop(PreviewProfile)
        robot.assertStackSize(1) // Profile (Details removed)
    }

    @Test
    fun pop_up_to_non_existent_destination() {
        val robot = NavigationHandlerTestRobot(PreviewHome)

        robot.navigate(PreviewDetails)
        robot.navigate(PreviewProfile)
        robot.assertStackSize(3)

        robot.navigateWithPopUpTo(
            destination = PreviewSettings,
            popUpTo = "non/existent",
            inclusive = false
        )
        // Should just add normally since popUpTo destination not found
        robot.assertTop(PreviewSettings)
        robot.assertStackSize(4)
    }

    // === Reuse If Exists ===

    @Test
    fun reuse_if_exists_true() {
        val robot = NavigationHandlerTestRobot(PreviewHome)

        robot.navigate(PreviewDetails)
        robot.navigate(PreviewProfile)
        robot.assertStackSize(3)

        robot.navigateWithReuse(PreviewDetails)
        robot.assertTop(PreviewDetails)
        robot.assertStackSize(3) // Stack size doesn't increase
    }

    @Test
    fun reuse_if_exists_false() {
        val robot = NavigationHandlerTestRobot(PreviewHome)

        robot.navigate(PreviewDetails)
        robot.navigate(PreviewProfile)
        robot.assertStackSize(3)

        robot.navigate(PreviewDetails) // Default reuse = false
        robot.assertTop(PreviewDetails)
        robot.assertStackSize(4) // Stack size increases
    }

    @Test
    fun reuse_if_exists_destination_not_in_stack() {
        val robot = NavigationHandlerTestRobot(PreviewHome)

        robot.navigate(PreviewDetails)
        robot.assertStackSize(2)

        robot.navigateWithReuse(PreviewProfile)
        robot.assertTop(PreviewProfile)
        robot.assertStackSize(3) // Added normally since not in stack
    }

    // === Clear Back Stack ===

    @Test
    fun clear_back_stack() {
        val robot = NavigationHandlerTestRobot(PreviewHome)

        robot.navigate(PreviewDetails)
        robot.navigate(PreviewProfile)
        robot.navigate(PreviewSettings)
        robot.assertStackSize(4)

        robot.navigateWithClearBackStack(PreviewHome)
        robot.assertTop(PreviewHome)
        robot.assertStackSize(1)
    }

    // === Complex Scenarios ===

    @Test
    fun pop_up_to_with_reuse() {
        val robot = NavigationHandlerTestRobot(PreviewHome)

        robot.navigate(PreviewDetails)
        robot.navigate(PreviewProfile)
        robot.navigate(PreviewSettings)
        robot.assertStackSize(4)

        robot.navigateWithPopUpToAndReuse(
            destination = PreviewProfile,
            popUpTo = PreviewDetails.id,
            inclusive = true
        )
        robot.assertTop(PreviewProfile)
        robot.assertStackSize(2) // Home + Profile
    }

    @Test
    fun navigate_to_same_destination_multiple_times_without_reuse() {
        val robot = NavigationHandlerTestRobot(PreviewHome)

        robot.navigate(PreviewDetails)
        robot.navigate(PreviewDetails)
        robot.navigate(PreviewDetails)

        robot.assertStackSize(4) // Home + Details + Details + Details
        robot.assertTop(PreviewDetails)
    }

    @Test
    fun pop_at_root_does_nothing() {
        val robot = NavigationHandlerTestRobot(PreviewHome)

        robot.pop()
        robot.assertTop(PreviewHome)
        robot.assertStackSize(1)
    }

    @Test
    fun pop_multiple_at_root_does_nothing() {
        val robot = NavigationHandlerTestRobot(PreviewHome)

        robot.popCount(5)
        robot.assertTop(PreviewHome)
        robot.assertStackSize(1)
    }
}