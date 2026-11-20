package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.module.GoodLockModule
import com.example.core.module.ModuleCategory
import com.example.core.module.ModuleRegistry
import com.example.core.preferences.ModuYouPreferences
import com.example.core.preferences.NotificationPreferences
import com.example.modules.automate.AutomateActionExecutor
import com.example.modules.automate.AutomateConfig
import com.example.modules.automate.AutomateController
import com.example.modules.automate.AutomateModule
import com.example.modules.automate.AutomationRoutine
import com.example.modules.automate.LiveConnectivityState
import com.example.modules.caffeine.CaffeineConfig
import com.example.modules.caffeine.CaffeineController
import com.example.modules.caffeine.CaffeineModule
import com.example.modules.clipboard.ClipboardConfig
import com.example.modules.clipboard.ClipboardController
import com.example.modules.clipboard.ClipboardEntry
import com.example.modules.clipboard.ClipboardModule
import com.example.modules.onehand.GestureAction
import com.example.modules.onehand.HandleConfig
import com.example.modules.onehand.HandleSide
import com.example.modules.onehand.OneHandActionExecutor
import com.example.modules.onehand.OneHandConfig
import com.example.modules.onehand.OneHandModule
import com.example.ui.theme.ThemeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val themeSettings: ThemeSettings = ThemeSettings(),
    val oneHandConfig: OneHandConfig = OneHandConfig(),
    val caffeineConfig: CaffeineConfig = CaffeineConfig(),
    val automateConfig: AutomateConfig = AutomateConfig(),
    val clipboardConfig: ClipboardConfig = ClipboardConfig(),
    val notificationPreferences: NotificationPreferences = NotificationPreferences(),
    val liveConnectivity: LiveConnectivityState = LiveConnectivityState(),
    val isAccessibilityEnabled: Boolean = false,
    val hasDndPolicyAccess: Boolean = false,
    val hasFlashlight: Boolean = false,
    val selectedCategory: ModuleCategory = ModuleCategory.ALL,
    val modules: List<GoodLockModule> = ModuleRegistry.modules,
    val lastSandboxFeedback: String? = null
)

private data class LocalUiFlags(
    val isAccessibilityEnabled: Boolean,
    val hasDndPolicyAccess: Boolean,
    val selectedCategory: ModuleCategory,
    val sandboxFeedback: String?,
    val connectivity: LiveConnectivityState
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = ModuYouPreferences.getInstance(application)
    private val isAccessibilityEnabledFlow = MutableStateFlow(
        OneHandModule.isAccessibilityServiceEnabled(application)
    )
    private val hasDndPolicyAccessFlow = MutableStateFlow(
        AutomateModule.hasDndPolicyAccess(application)
    )
    private val selectedCategoryFlow = MutableStateFlow(ModuleCategory.ALL)
    private val sandboxFeedbackFlow = MutableStateFlow<String?>(null)
    private val hasFlashlight = OneHandModule.hasFlashlightHardware(application)

    private val localFlagsFlow = combine(
        isAccessibilityEnabledFlow,
        hasDndPolicyAccessFlow,
        selectedCategoryFlow,
        sandboxFeedbackFlow,
        AutomateController.connectivityState
    ) { accEnabled, dndAccess, category, feedback, connectivity ->
        LocalUiFlags(accEnabled, dndAccess, category, feedback, connectivity)
    }

    val uiState: StateFlow<MainUiState> = combine(
        preferences.themeSettingsFlow,
        preferences.oneHandConfigFlow,
        preferences.caffeineConfigFlow,
        preferences.automateConfigFlow,
        preferences.clipboardConfigFlow,
        preferences.notificationPreferencesFlow,
        localFlagsFlow
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val theme = args[0] as ThemeSettings
        val oneHand = args[1] as OneHandConfig
        val caffeine = args[2] as CaffeineConfig
        val automate = args[3] as AutomateConfig
        val clipboard = args[4] as ClipboardConfig
        val notifPrefs = args[5] as NotificationPreferences
        val flags = args[6] as LocalUiFlags
        MainUiState(
            themeSettings = theme,
            oneHandConfig = oneHand,
            caffeineConfig = caffeine,
            automateConfig = automate,
            clipboardConfig = clipboard,
            notificationPreferences = notifPrefs,
            liveConnectivity = flags.connectivity,
            isAccessibilityEnabled = flags.isAccessibilityEnabled,
            hasDndPolicyAccess = flags.hasDndPolicyAccess,
            hasFlashlight = hasFlashlight,
            selectedCategory = flags.selectedCategory,
            modules = ModuleRegistry.filterByCategory(flags.selectedCategory),
            lastSandboxFeedback = flags.sandboxFeedback
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(
            isAccessibilityEnabled = isAccessibilityEnabledFlow.value,
            hasDndPolicyAccess = hasDndPolicyAccessFlow.value,
            hasFlashlight = hasFlashlight
        )
    )

    init {
        AutomateController.refreshLiveConnectivity(application)
        viewModelScope.launch {
            preferences.automateConfigFlow.collect { config ->
                AutomateController.syncMonitoringService(getApplication(), config)
            }
        }
    }

    fun refreshSystemStatus() {
        isAccessibilityEnabledFlow.value = OneHandModule.isAccessibilityServiceEnabled(getApplication())
        hasDndPolicyAccessFlow.value = AutomateModule.hasDndPolicyAccess(getApplication())
        CaffeineController.requestTileSync(getApplication())
        AutomateController.refreshLiveConnectivity(getApplication())
    }

    fun selectCategory(category: ModuleCategory) {
        selectedCategoryFlow.value = category
    }

    fun toggleModule(moduleId: String, enabled: Boolean) {
        when (moduleId) {
            CaffeineModule.MODULE_ID -> toggleCaffeine(enabled)
            AutomateModule.MODULE_ID -> toggleAutomateMaster(enabled)
            ClipboardModule.MODULE_ID -> toggleClipboardEnabled(enabled)
            else -> {
                viewModelScope.launch {
                    preferences.setModuleEnabled(moduleId, enabled)
                }
            }
        }
    }

    // --- Une Main + ---
    fun updateOneHandConfig(transform: (OneHandConfig) -> OneHandConfig) {
        viewModelScope.launch {
            val updated = transform(uiState.value.oneHandConfig)
            preferences.saveOneHandConfig(updated)
        }
    }

    fun updateHandleConfig(side: HandleSide, transform: (HandleConfig) -> HandleConfig) {
        updateOneHandConfig { current ->
            current.withHandle(side, transform(current.handleFor(side)))
        }
    }

    fun resetOneHandDefaults() {
        viewModelScope.launch {
            preferences.resetOneHandDefaults()
            sandboxFeedbackFlow.value = "Réglages par défaut restaurés"
        }
    }

    fun onSandboxGestureTriggered(side: HandleSide, action: GestureAction, isLong: Boolean) {
        val lengthLabel = if (isLong) "glissement long" else "glissement court"
        when (action) {
            GestureAction.FLASHLIGHT -> {
                val toggled = OneHandActionExecutor.toggleFlashlight(getApplication())
                sandboxFeedbackFlow.value = if (toggled) {
                    "${side.shortLabel} ($lengthLabel) → Lampe torche basculée"
                } else {
                    "${side.shortLabel} ($lengthLabel) → Flash indisponible sur cet appareil"
                }
            }
            GestureAction.CLIPBOARD -> {
                val latest = uiState.value.clipboardConfig.entries.firstOrNull()?.text
                sandboxFeedbackFlow.value = if (latest != null) {
                    "${side.shortLabel} ($lengthLabel) → Presse-papier : « ${latest.take(32)} »"
                } else {
                    "${side.shortLabel} ($lengthLabel) → Presse-papier récent"
                }
            }
            else -> {
                sandboxFeedbackFlow.value = "${side.shortLabel} ($lengthLabel) → ${action.label}"
            }
        }
    }

    // --- Caféine ---
    fun toggleCaffeine(active: Boolean) {
        if (active) {
            CaffeineController.activate(getApplication())
        } else {
            CaffeineController.deactivate(getApplication())
        }
    }

    fun updateCaffeineConfig(transform: (CaffeineConfig) -> CaffeineConfig) {
        viewModelScope.launch {
            preferences.updateCaffeineConfig(transform)
            if (uiState.value.caffeineConfig.isActive) {
                CaffeineController.activate(getApplication())
            }
        }
    }

    // --- Routines (ex-Automate) ---
    fun toggleAutomateMaster(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateAutomateConfig { it.copy(masterEnabled = enabled) }
        }
    }

    fun toggleRoutine(routineId: String, enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateAutomateConfig { config ->
                config.copy(
                    routines = config.routines.map { routine ->
                        if (routine.id == routineId) routine.copy(enabled = enabled) else routine
                    }
                )
            }
        }
    }

    fun saveRoutine(routine: AutomationRoutine) {
        viewModelScope.launch {
            preferences.updateAutomateConfig { config ->
                val exists = config.routines.any { it.id == routine.id }
                val updated = if (exists) {
                    config.routines.map { if (it.id == routine.id) routine else it }
                } else {
                    listOf(routine) + config.routines
                }
                config.copy(routines = updated)
            }
        }
    }

    fun deleteRoutine(routineId: String) {
        viewModelScope.launch {
            preferences.updateAutomateConfig { config ->
                config.copy(routines = config.routines.filterNot { it.id == routineId })
            }
        }
    }

    fun testRoutine(routine: AutomationRoutine) {
        viewModelScope.launch {
            AutomateActionExecutor.executeRoutine(
                context = getApplication(),
                routine = routine,
                triggerDetail = "Test manuel (${routine.triggerType.shortLabel})"
            )
        }
    }

    fun clearAutomateLogs() {
        viewModelScope.launch {
            preferences.updateAutomateConfig { it.copy(logs = emptyList()) }
        }
    }

    // --- Presse-papier ---
    fun toggleClipboardEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setModuleEnabled(ClipboardModule.MODULE_ID, enabled)
        }
    }

    fun captureClipboardNow() {
        viewModelScope.launch {
            ClipboardController.captureCurrentClipIfPresent(getApplication())
        }
    }

    fun copyClipboardEntry(entry: ClipboardEntry) {
        ClipboardController.copyToSystemClipboard(getApplication(), entry.text)
    }

    fun togglePinClipboardEntry(entryId: String) {
        viewModelScope.launch {
            preferences.updateClipboardEntries { entries ->
                entries.map { if (it.id == entryId) it.copy(pinned = !it.pinned) else it }
            }
        }
    }

    fun deleteClipboardEntry(entryId: String) {
        viewModelScope.launch {
            preferences.updateClipboardEntries { entries ->
                entries.filterNot { it.id == entryId }
            }
        }
    }

    fun clearUnpinnedClipboard() {
        viewModelScope.launch {
            preferences.updateClipboardEntries { entries ->
                entries.filter { it.pinned }
            }
        }
    }

    // --- Thème & Notifications ---
    fun updateThemeSettings(transform: (ThemeSettings) -> ThemeSettings) {
        viewModelScope.launch {
            preferences.updateThemeSettings(transform)
        }
    }

    fun setAllExecutionNotifications(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAllExecutionNotificationsEnabled(enabled)
        }
    }

    fun setCaffeineStatusBarIcon(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateCaffeineConfig { it.copy(showStatusBarIcon = enabled) }
        }
    }

    fun setRoutinesNotifications(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateAutomateConfig { it.copy(executionNotificationsEnabled = enabled) }
        }
    }
}
