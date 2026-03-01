package org.javafreedom.kdiab.profiles.domain.model

import kotlin.uuid.Uuid

data class User(
    val id: Uuid = Uuid.random(),
    val name: String
)
