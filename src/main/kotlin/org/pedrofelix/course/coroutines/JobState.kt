package org.pedrofelix.course.coroutines

import kotlinx.coroutines.Job

enum class JobState {
    NEW,
    ACTIVE_OR_COMPLETING,
    COMPLETED,
    CANCELLING,
    CANCELLED,
}

fun Job.getState(): JobState =
    if(isActive) {
        JobState.ACTIVE_OR_COMPLETING
    } else {
        if(isCompleted) {
            if(isCancelled) {
                JobState.CANCELLED
            } else {
                JobState.COMPLETED
            }
        }else {
            if(isCancelled) {
                JobState.CANCELLING
            }else{
                JobState.NEW
            }
        }
    }