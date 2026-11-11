package com.med.sleepmanager.integration

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyMapperPolicyTest {
    @Test fun unknownInitialStateNeverTriggersPause() {
        assertFalse(KeyMapperPolicy.shouldPause(KeyMapperMappingsState.UNKNOWN))
        assertFalse(KeyMapperPolicy.shouldPause(KeyMapperMappingsState.PAUSED))
        assertTrue(KeyMapperPolicy.shouldPause(KeyMapperMappingsState.ACTIVE))
    }

    @Test fun resumeOnlyOwnedPauseAndSkipWhenAlreadyActive() {
        assertFalse(KeyMapperPolicy.shouldResume(false, KeyMapperMappingsState.PAUSED))
        assertFalse(KeyMapperPolicy.shouldResume(false, KeyMapperMappingsState.UNKNOWN))
        assertFalse(KeyMapperPolicy.shouldResume(true, KeyMapperMappingsState.ACTIVE))
        assertTrue(KeyMapperPolicy.shouldResume(true, KeyMapperMappingsState.PAUSED))
        assertTrue(KeyMapperPolicy.shouldResume(true, KeyMapperMappingsState.UNKNOWN))
    }

    @Test fun stateApiRequiresKeyMapper47OrLater() {
        assertFalse(KeyMapperPolicy.supportsStateApi(null))
        assertFalse(KeyMapperPolicy.supportsStateApi("4.6.2"))
        assertFalse(KeyMapperPolicy.supportsStateApi("broken"))
        assertTrue(KeyMapperPolicy.supportsStateApi("4.7.0"))
        assertTrue(KeyMapperPolicy.supportsStateApi("4.7.0-beta1"))
        assertTrue(KeyMapperPolicy.supportsStateApi("5.0.0"))
    }
}
