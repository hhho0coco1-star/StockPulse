package com.stockpulse.common

data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ApiError? = null
) {
    companion object {
        fun <T> ok(data: T) = ApiResponse(success = true, data = data)
        fun <T> fail(code: String, message: String) =
            ApiResponse<T>(success = false, error = ApiError(code, message))
    }
}

data class ApiError(val code: String, val message: String)
