package com.xelvor.ai.assistant

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Opt-in accessibility foundation.
 *
 * This first milestone intentionally observes no content and performs no actions.
 * Any future automation must be tied to a user-reviewed task and cancellable.
 */
class XelvorAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally empty in the prototype.
    }

    override fun onInterrupt() {
        // No ongoing actions to interrupt in this milestone.
    }
}
