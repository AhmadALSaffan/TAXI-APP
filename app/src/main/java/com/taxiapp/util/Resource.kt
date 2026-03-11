package com.taxiapp.util

sealed class Resource<out T> {
    data class Success<out T>(val data: T) : Resource<T>()
    data class Error(val message: String)  : Resource<Nothing>()
    object Loading                         : Resource<Nothing>()
}

fun <A, B> Resource<A>.mapSuccess(transform: (A) -> B): Resource<B> = when (this) {
    is Resource.Success -> Resource.Success(transform(data))
    is Resource.Error   -> Resource.Error(message)
    Resource.Loading    -> Resource.Loading
}