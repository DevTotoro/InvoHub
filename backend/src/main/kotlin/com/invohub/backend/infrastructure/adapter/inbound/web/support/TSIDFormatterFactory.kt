package com.invohub.backend.infrastructure.adapter.inbound.web.support

import com.invohub.backend.domain.common.TSIDCodec
import org.springframework.format.AnnotationFormatterFactory
import org.springframework.format.Parser
import org.springframework.format.Printer

class TSIDFormatterFactory : AnnotationFormatterFactory<TSIDParam> {
    // Boxed on purpose: Long::class.java is the primitive long, and the conversion service looks converters
    // up by the boxed type, so registering the primitive would never match any parameter
    override fun getFieldTypes(): Set<Class<*>> = setOf(Long::class.javaObjectType)

    override fun getPrinter(
        annotation: TSIDParam,
        fieldType: Class<*>,
    ): Printer<Long> = Printer { objectValue, _ -> TSIDCodec.encode(objectValue) }

    override fun getParser(
        annotation: TSIDParam,
        fieldType: Class<*>,
    ): Parser<Long> = Parser { text, _ -> TSIDCodec.decode(text) }
}
