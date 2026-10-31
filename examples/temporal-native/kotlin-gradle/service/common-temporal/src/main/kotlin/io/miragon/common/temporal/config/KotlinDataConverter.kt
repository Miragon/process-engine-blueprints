package io.miragon.common.temporal.config

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.temporal.common.converter.DataConverter
import io.temporal.common.converter.DefaultDataConverter
import io.temporal.common.converter.JacksonJsonPayloadConverter

fun kotlinDataConverter(): DataConverter =
    DefaultDataConverter.newDefaultInstance()
        .withPayloadConverterOverrides(JacksonJsonPayloadConverter(jacksonObjectMapper()))
