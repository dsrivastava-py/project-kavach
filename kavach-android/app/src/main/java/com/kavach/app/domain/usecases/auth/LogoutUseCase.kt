package com.kavach.app.domain.usecases.auth

import com.kavach.app.domain.repository.AuthRepository
import javax.inject.Inject

/** Clear all auth state from DataStore — triggers auto-navigation to auth screen. */
class LogoutUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke() = authRepository.logout()
}
