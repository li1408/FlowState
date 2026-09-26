package com.markel.flowstate.core.data.local

typealias CompletionCommitStatus = com.markel.flowstate.core.domain.CompletionCommitStatus

data class ReopenTaskDbResult(
    val reopened: Boolean,
    val photoId: String? = null,
)
