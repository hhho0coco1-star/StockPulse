package com.stockpulse.ranking.controller

import com.stockpulse.common.ApiResponse
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.concurrent.TimeoutException

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleValidation(e: MethodArgumentNotValidException): ApiResponse<Nothing> {
        val msg = e.bindingResult.fieldErrors.joinToString { "${it.field}: ${it.defaultMessage}" }
        return ApiResponse.fail("VALIDATION_ERROR", msg)
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleMissingParam(e: MissingServletRequestParameterException) =
        ApiResponse.fail<Nothing>("MISSING_PARAM", "필수 파라미터 누락: ${e.parameterName}")

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleBadRequest(e: IllegalArgumentException) =
        ApiResponse.fail<Nothing>("BAD_REQUEST", e.message ?: "잘못된 요청")

    @ExceptionHandler(NoSuchElementException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleNotFound(e: NoSuchElementException) =
        ApiResponse.fail<Nothing>("NOT_FOUND", e.message ?: "리소스를 찾을 수 없습니다.")

    @ExceptionHandler(CallNotPermittedException::class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    fun handleCbOpen(e: CallNotPermittedException) =
        ApiResponse.fail<Nothing>("SERVICE_UNAVAILABLE", "일시적으로 서비스를 이용할 수 없습니다.")

    @ExceptionHandler(TimeoutException::class)
    @ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
    fun handleTimeout(e: TimeoutException) =
        ApiResponse.fail<Nothing>("TIMEOUT", "요청 시간이 초과되었습니다.")

    @ExceptionHandler(HttpMessageNotReadableException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleUnreadable(e: HttpMessageNotReadableException) =
        ApiResponse.fail<Nothing>("MALFORMED_REQUEST", "요청 본문을 해석할 수 없습니다.")

    @ExceptionHandler(MissingRequestHeaderException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleMissingHeader(e: MissingRequestHeaderException) =
        ApiResponse.fail<Nothing>("MISSING_HEADER", "필수 헤더 누락: ${e.headerName}")

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleGeneric(e: Exception): ApiResponse<Nothing> {
        log.error("처리되지 않은 예외", e)
        return ApiResponse.fail("INTERNAL_ERROR", "서버 오류가 발생했습니다.")
    }
}
