package com.example.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Services d’Andromate « tenus » par le service d’accessibilité (liaison permanente).
 *
 * Android impose une notification à tout service au premier plan. Un service tenu par le
 * service d’accessibilité reste vivant sans être au premier plan : il peut donc retirer sa
 * notification quand l’utilisateur l’a masquée dans les réglages d’Andromate.
 */
object ServiceKeeper {
    private val _held = MutableStateFlow<Set<String>>(emptySet())
    val held: StateFlow<Set<String>> = _held.asStateFlow()

    fun isHeld(serviceClass: Class<*>): Boolean = serviceClass.name in _held.value

    internal fun setHeld(serviceClass: Class<*>, held: Boolean) {
        _held.value = if (held) _held.value + serviceClass.name else _held.value - serviceClass.name
    }
}
